package com.naman.rupeeflow.qa

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.FriendGroupRepositoryImpl
import com.naman.rupeeflow.data.repository.FriendRepositoryImpl
import com.naman.rupeeflow.data.repository.TransactionRepositoryImpl
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Phase 1 — Comprehensive Friend Group Testing
 * Covers Group CRUD, Group Membership, and Group Filtering edge cases.
 */
@RunWith(RobolectricTestRunner::class)
class FriendGroupExhaustiveQATest {

    private lateinit var db: RupeeFlowDatabase
    private lateinit var groupRepo: FriendGroupRepositoryImpl
    private lateinit var friendRepo: FriendRepositoryImpl
    private lateinit var txnRepo: TransactionRepositoryImpl

    private val friend1 = Friend(id = 101L, name = "Aman Sharma", phoneNumber = "9876543210")
    private val friend2 = Friend(id = 102L, name = "Bhavna Patel", phoneNumber = "9876543211")
    private val friend3 = Friend(id = 103L, name = "Chetan Kumar", phoneNumber = "9876543212")
    private val friend4 = Friend(id = 104L, name = "Divya Singh", phoneNumber = "9876543213")

    @Before
    fun setUp() {
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            db = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            groupRepo = FriendGroupRepositoryImpl(db.friendGroupDao())
            friendRepo = FriendRepositoryImpl(db.friendDao())
            txnRepo = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(), db.friendDao())

            db.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "General", iconName = "category", colorHex = "#38BDF8", isDefault = true))

            db.friendDao().insertFriend(FriendEntity(id = 101L, name = friend1.name, phoneNumber = friend1.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 102L, name = friend2.name, phoneNumber = friend2.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 103L, name = friend3.name, phoneNumber = friend3.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 104L, name = friend4.name, phoneNumber = friend4.phoneNumber))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- GROUP CRUD TESTS ---

    @Test
    fun testCreateGroup_ValidData() = runBlocking {
        val groupId = groupRepo.createGroup("College Friends", "school", "#8B5CF6", listOf(101L, 102L))
        assertTrue(groupId > 0)
        val group = groupRepo.getGroupById(groupId)
        assertNotNull(group)
        assertEquals("College Friends", group!!.name)
        assertEquals("school", group.iconName)
        assertEquals("#8B5CF6", group.colorHex)
        assertEquals(2, group.memberCount)
        assertEquals(listOf(101L, 102L), group.memberFriendIds)
    }

    @Test
    fun testCreateEmptyGroup() = runBlocking {
        val groupId = groupRepo.createGroup("Empty Squad", null, null, emptyList())
        assertTrue(groupId > 0)
        val group = groupRepo.getGroupById(groupId)
        assertNotNull(group)
        assertEquals(0, group!!.memberCount)
        assertTrue(group.memberFriendIds.isEmpty())
    }

    @Test
    fun testRenameGroup() = runBlocking {
        val groupId = groupRepo.createGroup("Roomies", null, null, listOf(101L))
        val group = groupRepo.getGroupById(groupId)!!
        groupRepo.updateGroup(group.copy(name = "Permanent Roommates"), null)
        val updated = groupRepo.getGroupById(groupId)
        assertEquals("Permanent Roommates", updated!!.name)
    }

    @Test
    fun testUpdateGroupDetails_IconAndColor() = runBlocking {
        val groupId = groupRepo.createGroup("Trip 2026", "flight", "#06B6D4", listOf(101L, 102L))
        val group = groupRepo.getGroupById(groupId)!!
        val modified = group.copy(iconName = "beach_access", colorHex = "#F97316")
        groupRepo.updateGroup(modified, listOf(101L, 102L, 103L))
        val refreshed = groupRepo.getGroupById(groupId)!!
        assertEquals("beach_access", refreshed.iconName)
        assertEquals("#F97316", refreshed.colorHex)
        assertEquals(3, refreshed.memberCount)
    }

    @Test
    fun testDeleteGroup_RemovesGroupAndMembershipsOnly() = runBlocking {
        val groupId = groupRepo.createGroup("Temporary", null, null, listOf(101L, 102L))
        assertNotNull(groupRepo.getGroupById(groupId))
        groupRepo.deleteGroup(groupId)
        assertNull(groupRepo.getGroupById(groupId))
        // Verify friends themselves remain intact
        assertNotNull(friendRepo.getFriendById(101L))
        assertNotNull(friendRepo.getFriendById(102L))
    }

    @Test
    fun testDeleteGroupWithHistoricalTransactions_NeverAffectsTransactions() = runBlocking {
        val groupId = groupRepo.createGroup("Dinner Club", null, null, listOf(101L, 102L))
        val txn = Transaction(
            title = "Club Dinner",
            amountPaise = 200000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 100000L)
            )
        )
        val txnId = txnRepo.saveTransaction(txn)
        // Delete the group
        groupRepo.deleteGroup(groupId)

        // Historical transaction & participant shares must remain 100% untouched
        val retrievedTxn = txnRepo.getTransactionById(txnId)
        assertNotNull(retrievedTxn)
        assertEquals(200000L, retrievedTxn!!.amountPaise)
        assertEquals(2, retrievedTxn.participants.size)
        assertEquals(100000L, retrievedTxn.participants.first { it.friendId == 101L }.shareAmountPaise)
    }

    @Test
    fun testDuplicateGroupNames_AllowedDistinctGroups() = runBlocking {
        val id1 = groupRepo.createGroup("Office", null, null, listOf(101L))
        val id2 = groupRepo.createGroup("Office", null, null, listOf(102L))
        assertNotEquals(id1, id2)
        val allGroups = groupRepo.getAllGroupsList()
        assertEquals(2, allGroups.count { it.name == "Office" })
    }

    @Test
    fun testSpecialCharactersAndUnicodeGroupNames() = runBlocking {
        val unicodeName = "🚀 Goa Trip 🏖️ & Weekend 2026! (मित्र)"
        val groupId = groupRepo.createGroup(unicodeName, "flight", "#06B6D4", listOf(101L))
        val group = groupRepo.getGroupById(groupId)
        assertNotNull(group)
        assertEquals(unicodeName, group!!.name)
    }

    @Test
    fun testVeryLongGroupName() = runBlocking {
        val longName = "A".repeat(300)
        val groupId = groupRepo.createGroup(longName, null, null, listOf(101L))
        val group = groupRepo.getGroupById(groupId)
        assertNotNull(group)
        assertEquals(longName, group!!.name)
    }

    @Test
    fun testRapidRepeatedGroupCreationAndDeletion() = runBlocking {
        for (i in 1..10) {
            val gid = groupRepo.createGroup("Rapid $i", null, null, listOf(101L))
            assertTrue(gid > 0)
            groupRepo.deleteGroup(gid)
            assertNull(groupRepo.getGroupById(gid))
        }
        val finalGroups = groupRepo.getAllGroupsList()
        assertTrue(finalGroups.isEmpty())
    }

    @Test
    fun testConcurrentGroupUpdates() = runBlocking {
        val groupId = groupRepo.createGroup("Concurrent Group", null, null, listOf(101L))
        val updates = (1..5).map { index ->
            async {
                val group = groupRepo.getGroupById(groupId)
                if (group != null) {
                    groupRepo.updateGroup(group.copy(name = "Concurrent Update $index"), listOf(101L, 102L))
                }
            }
        }
        updates.awaitAll()
        val finalGroup = groupRepo.getGroupById(groupId)
        assertNotNull(finalGroup)
        assertTrue(finalGroup!!.name.startsWith("Concurrent Update"))
    }

    // --- GROUP MEMBERSHIP TESTS ---

    @Test
    fun testAddMultipleFriendsAndRemoveFriend() = runBlocking {
        val groupId = groupRepo.createGroup("Flat 402", null, null, listOf(101L))
        groupRepo.addFriendToGroup(groupId, 102L)
        groupRepo.addFriendToGroup(groupId, 103L)

        var group = groupRepo.getGroupById(groupId)!!
        assertEquals(3, group.memberCount)
        assertTrue(group.memberFriendIds.containsAll(listOf(101L, 102L, 103L)))

        groupRepo.removeFriendFromGroup(groupId, 102L)
        group = groupRepo.getGroupById(groupId)!!
        assertEquals(2, group.memberCount)
        assertFalse(group.memberFriendIds.contains(102L))
        assertTrue(group.memberFriendIds.contains(101L))
        assertTrue(group.memberFriendIds.contains(103L))
    }

    @Test
    fun testPreventDuplicateMembership() = runBlocking {
        val groupId = groupRepo.createGroup("Sports Club", null, null, listOf(101L, 101L, 101L))
        val group = groupRepo.getGroupById(groupId)!!
        assertEquals(1, group.memberCount)
        assertEquals(listOf(101L), group.memberFriendIds)

        // Adding again does not duplicate
        groupRepo.addFriendToGroup(groupId, 101L)
        val groupAfter = groupRepo.getGroupById(groupId)!!
        assertEquals(1, groupAfter.memberCount)
    }

    @Test
    fun testFriendBelongsToMultipleGroupsIndependently() = runBlocking {
        val g1 = groupRepo.createGroup("Group A", null, null, listOf(101L, 102L))
        val g2 = groupRepo.createGroup("Group B", null, null, listOf(101L, 103L))
        val g3 = groupRepo.createGroup("Group C", null, null, listOf(101L, 104L))

        val friend1Groups = groupRepo.getGroupsForFriend(101L).first()
        assertEquals(3, friend1Groups.size)

        // Removing 101 from Group B does not affect Group A or C
        groupRepo.removeFriendFromGroup(g2, 101L)
        val friend1GroupsAfter = groupRepo.getGroupsForFriend(101L).first()
        assertEquals(2, friend1GroupsAfter.size)
        val remainingIds = friend1GroupsAfter.map { it.id }
        assertTrue(remainingIds.contains(g1))
        assertFalse(remainingIds.contains(g2))
        assertTrue(remainingIds.contains(g3))
    }

    @Test
    fun testFriendWithoutAnyGroup() = runBlocking {
        // Friend 104 is not added to any group
        val groups = groupRepo.getGroupsForFriend(104L).first()
        assertTrue(groups.isEmpty())
    }

    @Test
    fun testAddArchivedFriendToGroup() = runBlocking {
        friendRepo.archiveFriend(103L)
        val archived = friendRepo.getFriendById(103L)!!
        assertTrue(archived.isArchived)

        // Can still maintain group association for historical viewing
        val groupId = groupRepo.createGroup("Past Mates", null, null, listOf(103L))
        val group = groupRepo.getGroupById(groupId)!!
        assertEquals(1, group.memberCount)
        assertTrue(group.memberFriendIds.contains(103L))
    }

    @Test
    fun testDeleteFriend_CascadesFromGroupsWithoutCorruptingOtherFriends() = runBlocking {
        val g1 = groupRepo.createGroup("Test Group", null, null, listOf(101L, 104L))
        // 104 has no financial history, so deleteFriend permanently deletes
        friendRepo.deleteFriend(friend4)
        assertNull(friendRepo.getFriendById(104L))

        // In the group, 104 should be cascaded out, while 101 remains
        val group = groupRepo.getGroupById(g1)!!
        assertEquals(1, group.memberCount)
        assertEquals(listOf(101L), group.memberFriendIds)
    }

    // --- GROUP FILTERING TESTS ---

    @Test
    fun testGroupFiltering_AllFriendsVsSpecificGroup() = runBlocking {
        val g1 = groupRepo.createGroup("Gym Buddies", null, null, listOf(101L, 102L))
        val allActiveFriends = friendRepo.getActiveFriends().first()
        assertEquals(4, allActiveFriends.size)

        val group1 = groupRepo.getGroupById(g1)!!
        val filtered = allActiveFriends.filter { it.id in group1.memberFriendIds }
        assertEquals(2, filtered.size)
        assertEquals(setOf(101L, 102L), filtered.map { it.id }.toSet())
    }

    @Test
    fun testSwitchGroupsFiltering() = runBlocking {
        val g1 = groupRepo.createGroup("Group 1", null, null, listOf(101L))
        val g2 = groupRepo.createGroup("Group 2", null, null, listOf(102L, 103L))
        val allActive = friendRepo.getActiveFriends().first()

        val filter1 = allActive.filter { it.id in groupRepo.getGroupById(g1)!!.memberFriendIds }
        assertEquals(listOf(101L), filter1.map { it.id })

        val filter2 = allActive.filter { it.id in groupRepo.getGroupById(g2)!!.memberFriendIds }
        assertEquals(setOf(102L, 103L), filter2.map { it.id }.toSet())
    }

    @Test
    fun testGroupDeletedWhileSelected_FallsBackToAllFriends() = runBlocking {
        val g1 = groupRepo.createGroup("To Delete", null, null, listOf(101L))
        var selectedGroupId: Long? = g1

        // Group is deleted
        groupRepo.deleteGroup(g1)
        val exists = groupRepo.getGroupById(selectedGroupId!!) != null
        if (!exists) {
            selectedGroupId = null // Fallback to All Friends
        }
        assertNull(selectedGroupId)
    }
}
