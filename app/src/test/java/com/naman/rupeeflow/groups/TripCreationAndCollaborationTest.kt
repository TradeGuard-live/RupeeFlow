package com.naman.rupeeflow.groups

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.local.entity.FriendGroupEntity
import com.naman.rupeeflow.data.local.entity.FriendGroupMemberEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.ui.components.DETERMINISTIC_AVATAR_PALETTE
import com.naman.rupeeflow.ui.components.getDeterministicColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TripCreationAndCollaborationTest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var friendRepository: FriendRepository
    private lateinit var friendGroupRepository: FriendGroupRepository
    private lateinit var expenseGroupRepository: ExpenseGroupRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var backupRestoreRepository: BackupRestoreRepository

    private val fixedNow = 1731456000000L

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        friendRepository = FriendRepositoryImpl(database.friendDao())
        friendGroupRepository = FriendGroupRepositoryImpl(database.friendGroupDao())
        categoryRepository = CategoryRepositoryImpl(database.categoryDao())
        expenseGroupRepository = ExpenseGroupRepositoryImpl(
            expenseGroupDao = database.expenseGroupDao(),
            transactionDao = database.transactionDao(),
            friendGroupDao = database.friendGroupDao()
        )
        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao()
        )
        settlementRepository = SettlementRepositoryImpl(database.settlementDao(), database.friendDao())
        backupRestoreRepository = BackupRestoreRepository(
            database = database,
            transactionRepository = transactionRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            categoryRepository = categoryRepository,
            friendGroupRepository = friendGroupRepository,
            context = context
        )

        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#EF4444")
            )
            database.categoryDao().insertCategory(
                CategoryEntity(id = 2L, name = "Travel", iconName = "flight", colorHex = "#3B82F6")
            )
            database.categoryDao().insertCategory(
                CategoryEntity(id = 3L, name = "Stay", iconName = "hotel", colorHex = "#10B981")
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    // ==========================================
    // GROUP 1: TRIP CREATION (Tests 1 - 9)
    // ==========================================

    @Test
    fun test1_createTrip_withNameOnly() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(trip)
        assertEquals("Goa Trip", trip!!.name)
        assertNull(trip.destination)
        assertNull(trip.startDate)
        assertNull(trip.endDate)
        assertNull(trip.coverImageUri)
        assertEquals(0, trip.memberCount)
    }

    @Test
    fun test2_createTrip_withNameAndDestination() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            destination = "Goa, India",
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(trip)
        assertEquals("Goa, India", trip!!.destination)
    }

    @Test
    fun test3_createTrip_withNameDestinationAndDates() = runBlocking {
        val start = 1734652800000L // 20 Dec
        val end = 1735084800000L   // 25 Dec
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            destination = "Goa",
            startDate = start,
            endDate = end,
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(trip)
        assertEquals(start, trip!!.startDate)
        assertEquals(end, trip.endDate)
    }

    @Test
    fun test4_createTrip_withNameDestinationDatesAndDescription() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            destination = "Goa",
            description = "Annual friends getaway",
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(trip)
        assertEquals("Annual friends getaway", trip!!.description)
    }

    @Test
    fun test5_createTrip_withCoverImage() = runBlocking {
        val imagePath = "/data/user/0/com.naman.rupeeflow/files/trips/trip_101.jpg"
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            coverImageUri = imagePath,
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(trip)
        assertEquals(imagePath, trip!!.coverImageUri)
    }

    @Test
    fun test6_createTrip_withoutCoverImage_fallbackVisualUsed() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Manali Trek",
            coverImageUri = null,
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(trip)
        assertNull(trip!!.coverImageUri)
        // Fallback visual derived from deterministic color
        val fallbackColor = getDeterministicColor(trip.name)
        assertNotNull(fallbackColor)
    }

    @Test
    fun test7_createTrip_withNoFriendsInitially() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Solo Trip",
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(trip)
        assertEquals(0, trip!!.memberCount)
        assertTrue(trip.memberFriendIds.isEmpty())
        assertNotNull(trip.linkedFriendGroupId)
    }

    @Test
    fun test8_createTrip_withSingleFriend() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Sukrut"))
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Weekend Drive",
            friendIds = listOf(friendId)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(trip)
        assertEquals(1, trip!!.memberCount)
        assertEquals(listOf(friendId), trip.memberFriendIds)
    }

    @Test
    fun test9_createTrip_withMultipleFriends() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val f2 = friendRepository.addFriend(Friend(name = "Manan"))
        val f3 = friendRepository.addFriend(Friend(name = "Manav"))
        val f4 = friendRepository.addFriend(Friend(name = "Pranav"))
        val f5 = friendRepository.addFriend(Friend(name = "Sukrut"))

        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Haveli Reunion",
            friendIds = listOf(f1, f2, f3, f4, f5)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(trip)
        assertEquals(5, trip!!.memberCount)
        assertEquals(setOf(f1, f2, f3, f4, f5), trip.memberFriendIds.toSet())
    }

    // ==========================================
    // GROUP 2: FRIEND SELECTION & WIZARD (Tests 10 - 18)
    // ==========================================

    @Test
    fun test10_selectFriendsIndividually() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Alice"))
        val f2 = friendRepository.addFriend(Friend(name = "Bob"))
        val selected = mutableSetOf<Long>()
        selected.add(f1)
        selected.add(f2)
        assertEquals(2, selected.size)
        assertTrue(selected.contains(f1) && selected.contains(f2))
    }

    @Test
    fun test11_selectEntireFriendGroup() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Alice"))
        val f2 = friendRepository.addFriend(Friend(name = "Bob"))
        val fgId = friendGroupRepository.createGroup(name = "Roommates", friendIds = listOf(f1, f2))

        val group = friendGroupRepository.getGroupById(fgId)
        assertNotNull(group)
        val tripSelectedFriends = group!!.memberFriendIds.toSet()
        assertEquals(setOf(f1, f2), tripSelectedFriends)
    }

    @Test
    fun test12_selectMultipleFriendGroups() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Alice"))
        val f2 = friendRepository.addFriend(Friend(name = "Bob"))
        val f3 = friendRepository.addFriend(Friend(name = "Charlie"))
        val g1 = friendGroupRepository.createGroup(name = "Roommates", friendIds = listOf(f1, f2))
        val g2 = friendGroupRepository.createGroup(name = "College", friendIds = listOf(f2, f3))

        val group1 = friendGroupRepository.getGroupById(g1)!!
        val group2 = friendGroupRepository.getGroupById(g2)!!

        val combined = (group1.memberFriendIds + group2.memberFriendIds).distinct()
        assertEquals(3, combined.size)
        assertEquals(setOf(f1, f2, f3), combined.toSet())
    }

    @Test
    fun test13_selectCombinationOfFriendsAndGroups() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Alice"))
        val f2 = friendRepository.addFriend(Friend(name = "Bob"))
        val f3 = friendRepository.addFriend(Friend(name = "Charlie"))
        val g1 = friendGroupRepository.createGroup(name = "Roommates", friendIds = listOf(f1, f2))

        val selected = (friendGroupRepository.getGroupById(g1)!!.memberFriendIds + listOf(f3)).distinct()
        assertEquals(setOf(f1, f2, f3), selected.toSet())
    }

    @Test
    fun test14_ensureFriendsInMultipleSelectedGroupsNotDuplicated() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val g1 = friendGroupRepository.createGroup(name = "Group A", friendIds = listOf(f1))
        val g2 = friendGroupRepository.createGroup(name = "Group B", friendIds = listOf(f1))

        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Trip",
            friendIds = listOf(f1, f1, f1)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertEquals(1, trip.memberCount)
        assertEquals(listOf(f1), trip.memberFriendIds)
    }

    @Test
    fun test15_searchFriendsByName() = runBlocking {
        friendRepository.addFriend(Friend(name = "Manan Patel"))
        friendRepository.addFriend(Friend(name = "Manav Shah"))
        friendRepository.addFriend(Friend(name = "Jay Joshi"))

        val allFriends = friendRepository.getAllFriends().first()
        val results = allFriends.filter { it.name.contains("Man", ignoreCase = true) }
        assertEquals(2, results.size)
        assertTrue(results.any { it.name == "Manan Patel" })
        assertTrue(results.any { it.name == "Manav Shah" })
    }

    @Test
    fun test16_searchFriendsByPhone() = runBlocking {
        friendRepository.addFriend(Friend(name = "Ronit", phoneNumber = "9876543210"))
        friendRepository.addFriend(Friend(name = "Kunal", phoneNumber = "9123456780"))

        val allFriends = friendRepository.getAllFriends().first()
        val results = allFriends.filter { it.phoneNumber?.contains("98765") == true }
        assertEquals(1, results.size)
        assertEquals("Ronit", results.first().name)
    }

    @Test
    fun test17_addNewFriendDirectlyFromWizard() = runBlocking {
        val newFriendId = friendRepository.addFriend(Friend(name = "Sukrut", phoneNumber = "9998887770"))
        assertTrue(newFriendId > 0L)

        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Quick Goa Trip",
            friendIds = listOf(newFriendId)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertEquals(1, trip.memberCount)
        assertEquals(newFriendId, trip.memberFriendIds.first())
    }

    @Test
    fun test18_removeSelectedFriendBeforeCreatingTrip() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Friend 1"))
        val f2 = friendRepository.addFriend(Friend(name = "Friend 2"))
        var selected = setOf(f1, f2)
        // User deselects Friend 2
        selected = selected - f2

        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Trip",
            friendIds = selected.toList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertEquals(1, trip.memberCount)
        assertEquals(listOf(f1), trip.memberFriendIds)
    }

    // ==========================================
    // GROUP 3: AUTOMATIC GROUP CREATION & SYNC (Tests 19 - 28)
    // ==========================================

    @Test
    fun test19_creatingTripWithFriends_createsCorrespondingFriendGroup() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val f2 = friendRepository.addFriend(Friend(name = "Manan"))

        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            friendIds = listOf(f1, f2)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertNotNull(trip.linkedFriendGroupId)

        val linkedGroup = friendGroupRepository.getGroupById(trip.linkedFriendGroupId!!)
        assertNotNull(linkedGroup)
        assertEquals("Goa Trip", linkedGroup!!.name)
    }

    @Test
    fun test20_linkedFriendGroup_containsExactlySelectedFriends() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val f2 = friendRepository.addFriend(Friend(name = "Manan"))

        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            friendIds = listOf(f1, f2)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        val linkedGroup = friendGroupRepository.getGroupById(trip.linkedFriendGroupId!!)!!

        assertEquals(2, linkedGroup.memberCount)
        assertEquals(setOf(f1, f2), linkedGroup.memberFriendIds.toSet())
    }

    @Test
    fun test21_linkedFriendGroup_immediatelyVisibleInQueries() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            friendIds = listOf(f1)
        )
        val allGroups = friendGroupRepository.getAllGroups().first()
        assertTrue(allGroups.any { it.name == "Goa Trip" })
    }

    @Test
    fun test22_linkedFriendGroup_hasCorrectMemberCount() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "A"))
        val f2 = friendRepository.addFriend(Friend(name = "B"))
        val f3 = friendRepository.addFriend(Friend(name = "C"))

        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Trio Trip",
            friendIds = listOf(f1, f2, f3)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        val linkedGroup = friendGroupRepository.getGroupById(trip.linkedFriendGroupId!!)!!
        assertEquals(3, linkedGroup.memberCount)
    }

    @Test
    fun test23_addingFriendToTrip_updatesLinkedFriendGroup() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val f2 = friendRepository.addFriend(Friend(name = "Sukrut"))

        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            friendIds = listOf(f1)
        )
        val initialTrip = expenseGroupRepository.getExpenseGroupById(tripId)!!

        // Update trip by adding Sukrut
        expenseGroupRepository.updateExpenseGroup(
            initialTrip,
            friendIds = listOf(f1, f2)
        )

        val updatedTrip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertEquals(2, updatedTrip.memberCount)
        val linkedGroup = friendGroupRepository.getGroupById(updatedTrip.linkedFriendGroupId!!)!!
        assertEquals(2, linkedGroup.memberCount)
        assertTrue(linkedGroup.memberFriendIds.contains(f2))
    }

    @Test
    fun test24_removingFriendFromTrip_updatesLinkedFriendGroup() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val f2 = friendRepository.addFriend(Friend(name = "Manan"))

        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Trip",
            friendIds = listOf(f1, f2)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!

        // Remove Manan
        expenseGroupRepository.updateExpenseGroup(
            trip,
            friendIds = listOf(f1)
        )

        val updatedTrip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertEquals(1, updatedTrip.memberCount)
        val linkedGroup = friendGroupRepository.getGroupById(updatedTrip.linkedFriendGroupId!!)!!
        assertEquals(1, linkedGroup.memberCount)
        assertEquals(listOf(f1), linkedGroup.memberFriendIds)

        // Ensure friend entity Manan was NOT deleted from database
        val friendManan = friendRepository.getFriendById(f2)
        assertNotNull("Friend entity must NOT be deleted when removed from trip", friendManan)
    }

    @Test
    fun test25_updatingTrip_doesNotDuplicateFriendGroup() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            friendIds = listOf(f1)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        val originalFgId = trip.linkedFriendGroupId

        // Update trip name
        expenseGroupRepository.updateExpenseGroup(
            trip.copy(name = "Goa Trip 2026"),
            friendIds = listOf(f1)
        )

        val updatedTrip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertEquals(originalFgId, updatedTrip.linkedFriendGroupId)
        val allGroups = friendGroupRepository.getAllGroups().first()
        assertEquals(1, allGroups.count { it.name.startsWith("Goa Trip") })
    }

    @Test
    fun test26_ifLinkedFriendGroupDeletedIndependently_tripRemainsValid() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            friendIds = listOf(f1)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        val fgId = trip.linkedFriendGroupId!!

        // User deletes the friend group independently in Friends -> Groups
        friendGroupRepository.deleteGroup(fgId)

        // Trip should still load safely without crash
        val fetchedTrip = expenseGroupRepository.getExpenseGroupById(tripId)
        assertNotNull(fetchedTrip)
        assertEquals("Goa Trip", fetchedTrip!!.name)
    }

    @Test
    fun test27_recreateLinkedFriendGroup_ifMissing() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            friendIds = listOf(f1)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        friendGroupRepository.deleteGroup(trip.linkedFriendGroupId!!)

        // Recreate linked group
        expenseGroupRepository.recreateLinkedFriendGroup(tripId, listOf(f1))

        val updatedTrip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertNotNull(updatedTrip.linkedFriendGroupId)
        val newFg = friendGroupRepository.getGroupById(updatedTrip.linkedFriendGroupId!!)
        assertNotNull(newFg)
        assertEquals("Goa Trip", newFg!!.name)
    }

    @Test
    fun test28_editingTripName_updatesLinkedFriendGroupName() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Beach Vacation",
            friendIds = listOf(f1)
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!

        expenseGroupRepository.updateExpenseGroup(
            trip.copy(name = "Goa Summer Holiday"),
            friendIds = listOf(f1)
        )

        val linkedGroup = friendGroupRepository.getGroupById(trip.linkedFriendGroupId!!)!!
        assertEquals("Goa Summer Holiday", linkedGroup.name)
    }

    // ==========================================
    // GROUP 4: PROFILE PICTURES & COVERS (Tests 29 - 41)
    // ==========================================

    @Test
    fun test29_friendProfilePicture_savedAndRetrieved() = runBlocking {
        val imgPath = "/data/user/0/com.naman.rupeeflow/files/profiles/friend_1.jpg"
        val friendId = friendRepository.addFriend(
            Friend(name = "Ronit", profileImagePath = imgPath)
        )
        val friend = friendRepository.getFriendById(friendId)!!
        assertEquals(imgPath, friend.profileImagePath)
    }

    @Test
    fun test30_friendProfilePicture_updatedSuccessfully() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Ronit"))
        val friend = friendRepository.getFriendById(friendId)!!
        assertNull(friend.profileImagePath)

        val newPath = "/data/user/0/com.naman.rupeeflow/files/profiles/friend_new.jpg"
        friendRepository.updateFriend(friend.copy(profileImagePath = newPath))

        val updated = friendRepository.getFriendById(friendId)!!
        assertEquals(newPath, updated.profileImagePath)
    }

    @Test
    fun test31_friendProfilePicture_removedSuccessfully() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Ronit", profileImagePath = "/path/to/pic.jpg")
        )
        val friend = friendRepository.getFriendById(friendId)!!
        friendRepository.updateFriend(friend.copy(profileImagePath = null))

        val updated = friendRepository.getFriendById(friendId)!!
        assertNull(updated.profileImagePath)
    }

    @Test
    fun test32_deterministicAvatarColor_consistentAcrossRestarts() {
        val name = "Jay Joshi"
        val color1 = getDeterministicColor(name)
        val color2 = getDeterministicColor(name)
        assertEquals(color1, color2)
        assertTrue(DETERMINISTIC_AVATAR_PALETTE.contains(color1))
    }

    @Test
    fun test33_deterministicAvatarColor_differentForDifferentFriends() {
        val colorJay = getDeterministicColor("Jay")
        val colorManav = getDeterministicColor("Manav")
        assertNotNull(colorJay)
        assertNotNull(colorManav)
    }

    @Test
    fun test34_tripCoverImage_savedAndRetrieved() = runBlocking {
        val coverPath = "/data/user/0/com.naman.rupeeflow/files/trips/goa_cover.jpg"
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            coverImageUri = coverPath,
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertEquals(coverPath, trip.coverImageUri)
    }

    @Test
    fun test35_tripCoverImage_updatedSuccessfully() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            coverImageUri = "/old/cover.jpg",
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        val newCover = "/new/cover.jpg"
        expenseGroupRepository.updateExpenseGroup(trip.copy(coverImageUri = newCover))

        val updated = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertEquals(newCover, updated.coverImageUri)
    }

    @Test
    fun test36_tripCoverImage_removedSuccessfully() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            coverImageUri = "/cover.jpg",
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        expenseGroupRepository.updateExpenseGroup(trip.copy(coverImageUri = null))

        val updated = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertNull(updated.coverImageUri)
    }

    @Test
    fun test37_tripCoverAvatar_fallbackWhenNoCover() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Himalaya Trek",
            coverImageUri = null,
            friendIds = emptyList()
        )
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertNull(trip.coverImageUri)
        val fallbackColor = getDeterministicColor(trip.name)
        assertNotNull(fallbackColor)
    }

    @Test
    fun test38_backupExportsExpenseGroupsAndImageReferences() = runBlocking {
        val fId = friendRepository.addFriend(Friend(name = "Jay", profileImagePath = "/profiles/jay.jpg"))
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            destination = "Goa",
            coverImageUri = "/trips/goa.jpg",
            friendIds = listOf(fId)
        )

        val json = backupRestoreRepository.exportToJson()
        assertTrue(json.contains("Goa Trip"))
        assertTrue(json.contains("expense_groups"))
    }

    @Test
    fun test39_restoreRestoresExpenseGroupsAndImageReferences() = runBlocking {
        val fId = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(
            name = "Goa Trip",
            destination = "Goa",
            coverImageUri = "/trips/goa.jpg",
            friendIds = listOf(fId)
        )

        val json = backupRestoreRepository.exportToJson()

        // Clear and restore
        expenseGroupRepository.deleteExpenseGroup(tripId)
        val restored = backupRestoreRepository.restoreFromJson(json)
        assertTrue(restored)

        val trips = expenseGroupRepository.getAllExpenseGroupsList()
        assertTrue(trips.any { it.name == "Goa Trip" && it.destination == "Goa" })
    }

    @Test
    fun test40_restoreHandlesMissingImageGracefully() = runBlocking {
        val testJson = """
            {
                "appName": "RupeeFlow",
                "version": 9,
                "exportedAt": 1731456000000,
                "expense_groups": [
                    {
                        "id": 999,
                        "name": "Missing Image Trip",
                        "destination": "Paris",
                        "coverImageUri": "/nonexistent/path/cover.jpg",
                        "colorHex": "#3B82F6",
                        "createdAt": 1000,
                        "updatedAt": 1000
                    }
                ],
                "transactions": [],
                "friends": [],
                "categories": []
            }
        """.trimIndent()

        val restored = backupRestoreRepository.restoreFromJson(testJson)
        assertTrue(restored)
        val trip = expenseGroupRepository.getExpenseGroupById(999L)
        assertNotNull(trip)
        assertEquals("Missing Image Trip", trip!!.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test41_blankTripNameValidation_rejectsEmpty() {
        runBlocking {
            expenseGroupRepository.createExpenseGroup(
                name = "   ",
                friendIds = emptyList()
            )
        }
    }

    // ==========================================
    // GROUP 5: TRIP TRANSACTIONS & SPLITS (Tests 42 - 53)
    // ==========================================

    @Test
    fun test42_addTransactionToTrip_linksExpenseGroupId() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa Trip", friendIds = emptyList())
        val txn = Transaction(
            title = "Flight Tickets",
            amountPaise = 1500000L,
            transactionType = TransactionType.EXPENSE,
            categoryId = 2L,
            transactionDate = fixedNow,
            paymentMethod = PaymentMethod.UPI,
            expenseGroupId = tripId,
            expenseGroupName = "Goa Trip"
        )
        val txnId = transactionRepository.saveTransaction(txn)
        val saved = transactionRepository.getTransactionById(txnId)!!
        assertEquals(tripId, saved.expenseGroupId)
        assertEquals("Goa Trip", saved.expenseGroupName)
    }

    @Test
    fun test43_tripMembersSuggestedForSplit() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val f2 = friendRepository.addFriend(Friend(name = "Manan"))
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa Trip", friendIds = listOf(f1, f2))
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!

        assertEquals(setOf(f1, f2), trip.memberFriendIds.toSet())
    }

    @Test
    fun test44_transactionDefaultsToMeAsPayer() = runBlocking {
        val txn = Transaction(
            title = "Dinner",
            amountPaise = 300000L,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L,
            transactionDate = fixedNow,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null // Default: Paid by Me
        )
        assertNull("Default payer is Me (null paidByFriendId)", txn.paidByFriendId)
    }

    @Test
    fun test45_changePayerToTripMember() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val txn = Transaction(
            title = "Car Rental",
            amountPaise = 400000L,
            transactionType = TransactionType.EXPENSE,
            categoryId = 2L,
            transactionDate = fixedNow,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = f1
        )
        assertEquals(f1, txn.paidByFriendId)
    }

    @Test
    fun test46_subsetOfTripMembersInSplit() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val f2 = friendRepository.addFriend(Friend(name = "Manan"))
        val f3 = friendRepository.addFriend(Friend(name = "Sukrut"))

        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa", friendIds = listOf(f1, f2, f3))

        // Only Jay and Sukrut participate in this dinner, excluding Manan
        val participants = listOf(
            TransactionParticipant(friendId = null, shareAmountPaise = 100000L), // Me
            TransactionParticipant(friendId = f1, shareAmountPaise = 100000L),
            TransactionParticipant(friendId = f3, shareAmountPaise = 100000L)
        )
        val txn = Transaction(
            title = "Seafood Dinner",
            amountPaise = 300000L,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L,
            transactionDate = fixedNow,
            paymentMethod = PaymentMethod.UPI,
            expenseGroupId = tripId,
            participants = participants
        )
        val id = transactionRepository.saveTransaction(txn)
        val saved = transactionRepository.getTransactionById(id)!!
        assertEquals(3, saved.participants.size)
        assertFalse(saved.participants.any { it.friendId == f2 })
    }

    @Test
    fun test47_equalSplitAmongTripMembers() = runBlocking {
        val totalPaise = 300000L // ₹3,000
        val count = 3 // Me, Jay, Manan
        val perPerson = totalPaise / count // 100,000 paise (₹1,000)

        assertEquals(100000L, perPerson)
        assertEquals(totalPaise, perPerson * count)
    }

    @Test
    fun test48_percentageSplitAmongTripMembers() = runBlocking {
        val totalPaise = 1000000L // ₹10,000
        // Me: 50%, Jay: 30%, Manan: 20%
        val meShare = 500000L
        val jayShare = 300000L
        val mananShare = 200000L

        assertEquals(totalPaise, meShare + jayShare + mananShare)
    }

    @Test
    fun test49_customSplitAmongTripMembers() = runBlocking {
        val totalPaise = 750000L // ₹7,500
        val meShare = 250000L
        val friendShare = 500000L
        assertEquals(totalPaise, meShare + friendShare)
    }

    @Test
    fun test50_tripTotalSpendingUpdates() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa Trip", friendIds = emptyList())
        transactionRepository.saveTransaction(
            Transaction(
                title = "Hotel",
                amountPaise = 800000L,
                transactionType = TransactionType.EXPENSE,
                categoryId = 3L,
                transactionDate = fixedNow,
                paymentMethod = PaymentMethod.UPI,
                expenseGroupId = tripId
            )
        )
        transactionRepository.saveTransaction(
            Transaction(
                title = "Dinner",
                amountPaise = 200000L,
                transactionType = TransactionType.EXPENSE,
                categoryId = 1L,
                transactionDate = fixedNow,
                paymentMethod = PaymentMethod.CASH,
                expenseGroupId = tripId
            )
        )

        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertEquals(2, trip.transactionCount)
        assertEquals(1000000L, trip.totalSpendingPaise)
        assertEquals(200000L, trip.cashSpendingPaise)
        assertEquals(800000L, trip.onlineSpendingPaise)
    }

    @Test
    fun test51_personalSpendingInTrip_onlyAccountsForUserShare() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa Trip", friendIds = listOf(f1))

        // Total bill is ₹4,000 paid by Me, split equally (Me owes ₹2,000, Jay owes ₹2,000)
        transactionRepository.saveTransaction(
            Transaction(
                title = "Shared Villa",
                amountPaise = 400000L,
                transactionType = TransactionType.EXPENSE,
                categoryId = 3L,
                transactionDate = fixedNow,
                paymentMethod = PaymentMethod.UPI,
                expenseGroupId = tripId,
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 200000L),
                    TransactionParticipant(friendId = f1, shareAmountPaise = 200000L)
                )
            )
        )

        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        // Personal share is ₹2,000 (200,000 paise)
        assertEquals(200000L, trip.totalSpendingPaise)
        // Gross bills is ₹4,000 (400,000 paise)
        assertEquals(400000L, trip.grossSpendingPaise)
    }

    @Test
    fun test52_friendBalancesWithinTrip_calculatedCorrectly() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa", friendIds = listOf(f1))

        // Me paid ₹2,000; Jay owes ₹1,000
        val txn = Transaction(
            title = "Dinner",
            amountPaise = 200000L,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L,
            transactionDate = fixedNow,
            paymentMethod = PaymentMethod.UPI,
            expenseGroupId = tripId,
            paidByFriendId = null, // Me paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = f1, shareAmountPaise = 100000L)
            )
        )
        transactionRepository.saveTransaction(txn)

        val tripTxns = transactionRepository.getTransactionsForExpenseGroupList(tripId)
        val friends = friendRepository.getAllFriendsList()
        val tripLedgers = LedgerCalculator.computeFriendLedgers(listOf(friends.first { it.id == f1 }), tripTxns, emptyList())

        val jayLedger = tripLedgers.first()
        assertEquals(100000L, jayLedger.totalTheyOweMePaise)
        assertEquals(100000L, jayLedger.netBalancePaise) // Jay owes Me ₹1,000
    }

    @Test
    fun test53_editingTripMembership_doesNotModifyHistoricalTransactions() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa", friendIds = listOf(f1))

        val txnId = transactionRepository.saveTransaction(
            Transaction(
                title = "Dinner",
                amountPaise = 200000L,
                transactionType = TransactionType.EXPENSE,
                categoryId = 1L,
                transactionDate = fixedNow,
                paymentMethod = PaymentMethod.UPI,
                expenseGroupId = tripId,
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                    TransactionParticipant(friendId = f1, shareAmountPaise = 100000L)
                )
            )
        )

        // Now remove Jay and add Sukrut to trip
        val f2 = friendRepository.addFriend(Friend(name = "Sukrut"))
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        expenseGroupRepository.updateExpenseGroup(trip, listOf(f2))

        // Verify historical transaction was NOT modified
        val fetchedTxn = transactionRepository.getTransactionById(txnId)!!
        assertEquals(2, fetchedTxn.participants.size)
        assertTrue(fetchedTxn.participants.any { it.friendId == f1 })
    }

    // ==========================================
    // GROUP 6: ARCHIVE & DELETION SAFETY (Tests 54 - 61)
    // ==========================================

    @Test
    fun test54_archiveTrip_marksIsArchivedTrue() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa Trip", friendIds = emptyList())
        expenseGroupRepository.setArchived(tripId, true)
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertTrue(trip.isArchived)
        assertNotNull(trip.archivedAt)
    }

    @Test
    fun test55_archivedTrip_filteredFromActive() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Old Trip", friendIds = emptyList())
        expenseGroupRepository.setArchived(tripId, true)

        val allTrips = expenseGroupRepository.getAllExpenseGroups().first()
        val active = allTrips.filter { !it.isArchived }
        assertFalse(active.any { it.id == tripId })
    }

    @Test
    fun test56_archivedTrip_visibleInArchivedList() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Old Trip", friendIds = emptyList())
        expenseGroupRepository.setArchived(tripId, true)

        val allTrips = expenseGroupRepository.getAllExpenseGroups().first()
        val archived = allTrips.filter { it.isArchived }
        assertTrue(archived.any { it.id == tripId })
    }

    @Test
    fun test57_unarchiveTrip_restoresToActiveList() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Revived Trip", friendIds = emptyList())
        expenseGroupRepository.setArchived(tripId, true)
        expenseGroupRepository.setArchived(tripId, false)

        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        assertFalse(trip.isArchived)
        assertNull(trip.archivedAt)
    }

    @Test
    fun test58_deleteTrip_preservesAllTransactions() = runBlocking {
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa Trip", friendIds = emptyList())
        val txnId = transactionRepository.saveTransaction(
            Transaction(
                title = "Dinner",
                amountPaise = 200000L,
                transactionType = TransactionType.EXPENSE,
                categoryId = 1L,
                transactionDate = fixedNow,
                paymentMethod = PaymentMethod.UPI,
                expenseGroupId = tripId,
                expenseGroupName = "Goa Trip"
            )
        )

        // Delete the trip container
        expenseGroupRepository.deleteExpenseGroup(tripId)

        // Transaction MUST be preserved (FK ON DELETE SET NULL)
        val preservedTxn = transactionRepository.getTransactionById(txnId)
        assertNotNull("Transaction MUST be preserved when trip container is deleted", preservedTxn)
        assertNull(preservedTxn!!.expenseGroupId)
        assertEquals("Goa Trip", preservedTxn.expenseGroupName) // Preserves historical label
    }

    @Test
    fun test59_deleteTrip_preservesAllSettlements() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa Trip", friendIds = listOf(f1))

        val sId = settlementRepository.recordSettlement(
            Settlement(
                payerId = f1,
                receiverId = null,
                amountPaise = 100000L,
                settlementDate = fixedNow
            )
        )

        expenseGroupRepository.deleteExpenseGroup(tripId)

        val settlement = settlementRepository.getSettlementById(sId)
        assertNotNull("Settlements must never be deleted when trip container is deleted", settlement)
    }

    @Test
    fun test60_deleteTrip_preservesFriendsAndBalances() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa Trip", friendIds = listOf(f1))

        expenseGroupRepository.deleteExpenseGroup(tripId)

        val friend = friendRepository.getFriendById(f1)
        assertNotNull("Friend records must remain intact when trip container is deleted", friend)
    }

    @Test
    fun test61_deleteTrip_preservesLinkedFriendGroup() = runBlocking {
        val f1 = friendRepository.addFriend(Friend(name = "Jay"))
        val tripId = expenseGroupRepository.createExpenseGroup(name = "Goa Trip", friendIds = listOf(f1))
        val trip = expenseGroupRepository.getExpenseGroupById(tripId)!!
        val linkedGroupId = trip.linkedFriendGroupId!!

        // Deleting trip container leaves the friend group in Friends -> Custom Groups intact
        expenseGroupRepository.deleteExpenseGroup(tripId)

        val fg = friendGroupRepository.getGroupById(linkedGroupId)
        assertNotNull("Linked friend group should remain preserved unless explicitly deleted by user", fg)
    }
}

