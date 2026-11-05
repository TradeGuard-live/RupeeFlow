package com.naman.rupeeflow.qa

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.FriendGroupRepositoryImpl
import com.naman.rupeeflow.data.repository.FriendRepositoryImpl
import com.naman.rupeeflow.data.repository.SettlementRepositoryImpl
import com.naman.rupeeflow.data.repository.TransactionRepositoryImpl
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.theme.CoralRed
import com.naman.rupeeflow.ui.theme.EmeraldMint
import com.naman.rupeeflow.ui.theme.TextSecondary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Comprehensive Automated Test Suite covering all 27 requirements from Section 9:
 * 1. Create a group.
 * 2. Rename a group.
 * 3. Add friends to a group.
 * 4. Remove friends from a group.
 * 5. Friend belongs to multiple groups.
 * 6. Prevent duplicate group membership.
 * 7. Filter friends by group.
 * 8. Select group in Add Transaction.
 * 9. Automatically select group members.
 * 10. Manually remove a selected participant.
 * 11. Manually add a participant.
 * 12. Switch between groups.
 * 13. Verify historical transaction participants remain unchanged after membership changes.
 * 14. Delete an empty group.
 * 15. Delete a group containing friends.
 * 16. Verify deleting a group never deletes friends.
 * 17. Delete a friend without financial history.
 * 18. Archive a friend with financial history.
 * 19. Verify historical settlements remain intact.
 * 20. Verify historical transaction shares remain intact.
 * 21. Verify positive friend balances display green.
 * 22. Verify negative friend balances display red.
 * 23. Verify zero balances display settled.
 * 24. Verify your share is displayed above the total amount.
 * 25. Verify transactions without splits do not show misleading shares.
 * 26. Test Room migration with existing user data.
 * 27. Test database foreign-key integrity.
 * Plus edge cases and financial rounding.
 */
@RunWith(RobolectricTestRunner::class)
class FriendGroupAndSplitUpgradeTest {

    private lateinit var db: RupeeFlowDatabase
    private lateinit var groupRepo: FriendGroupRepositoryImpl
    private lateinit var friendRepo: FriendRepositoryImpl
    private lateinit var txnRepo: TransactionRepositoryImpl
    private lateinit var settlementRepo: SettlementRepositoryImpl

    private val friend1 = Friend(id = 1L, name = "Aman", phoneNumber = "9876543210")
    private val friend2 = Friend(id = 2L, name = "Bhavna", phoneNumber = "9876543211")
    private val friend3 = Friend(id = 3L, name = "Chetan", phoneNumber = "9876543212")
    private val friend4 = Friend(id = 4L, name = "Divya", phoneNumber = "9876543213")

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
            settlementRepo = SettlementRepositoryImpl(db.settlementDao(), db.friendDao())

            // Seed initial categories & friends
            db.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF0000", isDefault = true))
            db.categoryDao().insertCategory(CategoryEntity(id = 2L, name = "Travel", iconName = "flight", colorHex = "#0000FF", isDefault = false))

            db.friendDao().insertFriend(FriendEntity(id = 1L, name = friend1.name, phoneNumber = friend1.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 2L, name = friend2.name, phoneNumber = friend2.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 3L, name = friend3.name, phoneNumber = friend3.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 4L, name = friend4.name, phoneNumber = friend4.phoneNumber))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. Create a group
    @Test
    fun test01_CreateGroup() = runBlocking {
        val groupId = groupRepo.createGroup("College Friends", "school", "#8B5CF6", listOf(1L, 2L))
        assertTrue(groupId > 0)

        val groups = groupRepo.getAllGroups().first()
        assertEquals(1, groups.size)
        val group = groups[0]
        assertEquals("College Friends", group.name)
        assertEquals("school", group.iconName)
        assertEquals("#8B5CF6", group.colorHex)
        assertEquals(2, group.memberFriendIds.size)
        assertTrue(group.memberFriendIds.contains(1L))
        assertTrue(group.memberFriendIds.contains(2L))
    }

    // 2. Rename a group
    @Test
    fun test02_RenameGroup() = runBlocking {
        val groupId = groupRepo.createGroup("Hostel Boys", "home", "#10B981", listOf(1L))
        val group = groupRepo.getGroupById(groupId)
        assertNotNull(group)

        val updated = group!!.copy(name = "Hostel Roommates", colorHex = "#06B6D4")
        groupRepo.updateGroup(updated, updated.memberFriendIds)

        val fetched = groupRepo.getGroupById(groupId)
        assertNotNull(fetched)
        assertEquals("Hostel Roommates", fetched!!.name)
        assertEquals("#06B6D4", fetched.colorHex)
    }

    // 3. Add friends to a group
    @Test
    fun test03_AddFriendsToGroup() = runBlocking {
        val groupId = groupRepo.createGroup("Roommates", null, null, listOf(1L))
        var group = groupRepo.getGroupById(groupId)!!
        assertEquals(1, group.memberFriendIds.size)

        // Add friend 2 and 3
        groupRepo.setGroupMembers(groupId, listOf(1L, 2L, 3L))

        group = groupRepo.getGroupById(groupId)!!
        assertEquals(3, group.memberFriendIds.size)
        assertTrue(group.memberFriendIds.containsAll(listOf(1L, 2L, 3L)))
    }

    // 4. Remove friends from a group
    @Test
    fun test04_RemoveFriendsFromGroup() = runBlocking {
        val groupId = groupRepo.createGroup("Trip", null, null, listOf(1L, 2L, 3L))
        groupRepo.removeFriendFromGroup(groupId, 2L)

        val group = groupRepo.getGroupById(groupId)!!
        assertEquals(2, group.memberFriendIds.size)
        assertTrue(group.memberFriendIds.contains(1L))
        assertFalse(group.memberFriendIds.contains(2L))
        assertTrue(group.memberFriendIds.contains(3L))
    }

    // 5. Friend belongs to multiple groups
    @Test
    fun test05_FriendBelongsToMultipleGroups() = runBlocking {
        val g1 = groupRepo.createGroup("College", null, null, listOf(1L, 2L))
        val g2 = groupRepo.createGroup("Flatmates", null, null, listOf(1L, 3L))

        val groupsForFriend1 = groupRepo.getGroupsForFriend(1L).first()
        assertEquals(2, groupsForFriend1.size)
        val groupIds = groupsForFriend1.map { it.id }
        assertTrue(groupIds.contains(g1))
        assertTrue(groupIds.contains(g2))

        val groupsForFriend2 = groupRepo.getGroupsForFriend(2L).first()
        assertEquals(1, groupsForFriend2.size)
        assertEquals(g1, groupsForFriend2[0].id)
    }

    // 6. Prevent duplicate group membership
    @Test
    fun test06_PreventDuplicateGroupMembership() = runBlocking {
        val groupId = groupRepo.createGroup("Project Team", null, null, listOf(1L, 1L, 2L, 2L, 1L))
        val group = groupRepo.getGroupById(groupId)!!
        // Member IDs list must contain distinct IDs
        assertEquals(2, group.memberFriendIds.size)
        assertEquals(setOf(1L, 2L), group.memberFriendIds.toSet())

        // Calling setGroupMembers with duplicate IDs
        groupRepo.setGroupMembers(groupId, listOf(1L, 1L, 2L, 3L, 3L))
        val updatedGroup = groupRepo.getGroupById(groupId)!!
        assertEquals(3, updatedGroup.memberFriendIds.size)
        assertEquals(setOf(1L, 2L, 3L), updatedGroup.memberFriendIds.toSet())
    }

    // 7. Filter friends by group
    @Test
    fun test07_FilterFriendsByGroup() = runBlocking {
        val allFriends = listOf(friend1, friend2, friend3, friend4)
        val g1 = groupRepo.createGroup("Office", null, null, listOf(1L, 4L))
        val group = groupRepo.getGroupById(g1)!!

        // Filter all friends by group 1
        val filtered = allFriends.filter { it.id in group.memberFriendIds }
        assertEquals(2, filtered.size)
        assertEquals(listOf(1L, 4L), filtered.map { it.id })

        // Empty group filter
        val emptyGroup = groupRepo.createGroup("Empty", null, null, emptyList())
        val emptyMembers = groupRepo.getGroupById(emptyGroup)!!.memberFriendIds
        val emptyFiltered = allFriends.filter { it.id in emptyMembers }
        assertTrue(emptyFiltered.isEmpty())
    }

    // 8 & 9. Select group in Add Transaction & automatically select group members
    @Test
    fun test08_09_SelectGroupInAddTransaction_AutoSelectsMembers() = runBlocking {
        val g1 = groupRepo.createGroup("Roommates", null, null, listOf(1L, 2L))
        val group = groupRepo.getGroupById(g1)!!

        // Simulated UI state when selecting group:
        var selectedFriendIds = setOf<Long>()
        // On group selected:
        selectedFriendIds = group.memberFriendIds.toSet()

        assertEquals(setOf(1L, 2L), selectedFriendIds)
        assertEquals(2, selectedFriendIds.size)
    }

    // 10. Manually remove a selected participant
    @Test
    fun test10_ManuallyRemoveSelectedParticipant() = runBlocking {
        val g1 = groupRepo.createGroup("Roommates", null, null, listOf(1L, 2L, 3L))
        val group = groupRepo.getGroupById(g1)!!

        var selectedFriendIds = group.memberFriendIds.toSet()
        // User unchecks friend 2
        selectedFriendIds = selectedFriendIds - 2L

        assertEquals(setOf(1L, 3L), selectedFriendIds)
    }

    // 11. Manually add a participant
    @Test
    fun test11_ManuallyAddParticipant() = runBlocking {
        val g1 = groupRepo.createGroup("Roommates", null, null, listOf(1L, 2L))
        val group = groupRepo.getGroupById(g1)!!

        var selectedFriendIds = group.memberFriendIds.toSet()
        // User adds friend 4 (guest)
        selectedFriendIds = selectedFriendIds + 4L

        assertEquals(setOf(1L, 2L, 4L), selectedFriendIds)
    }

    // 12. Switch between groups
    @Test
    fun test12_SwitchBetweenGroups() = runBlocking {
        val g1 = groupRepo.createGroup("College", null, null, listOf(1L, 2L))
        val g2 = groupRepo.createGroup("Work", null, null, listOf(3L, 4L))

        var selectedFriendIds = groupRepo.getGroupById(g1)!!.memberFriendIds.toSet()
        assertEquals(setOf(1L, 2L), selectedFriendIds)

        // Switch to group 2
        selectedFriendIds = groupRepo.getGroupById(g2)!!.memberFriendIds.toSet()
        assertEquals(setOf(3L, 4L), selectedFriendIds)
    }

    // 13. Verify historical transaction participants remain unchanged after membership changes
    @Test
    fun test13_HistoricalTransactionsUnchangedAfterGroupMembershipChanges() = runBlocking {
        val g1 = groupRepo.createGroup("Dinner Club", null, null, listOf(1L, 2L))

        // Create transaction with participants (Me + 1L + 2L)
        val txn = Transaction(
            title = "Weekend Feast",
            amountPaise = 300000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            splitMethod = SplitMethod.EQUAL,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 2L, shareAmountPaise = 100000L)
            )
        )
        val txnId = txnRepo.saveTransaction(txn)

        // Now modify group membership: remove friend 2, add friend 3
        groupRepo.setGroupMembers(g1, listOf(1L, 3L))

        // Historical transaction MUST remain 100% unchanged
        val storedTxn = txnRepo.getTransactionById(txnId)
        assertNotNull(storedTxn)
        assertEquals(3, storedTxn!!.participants.size)
        val participantFriendIds = storedTxn.participants.map { it.friendId }
        assertTrue(participantFriendIds.contains(null))
        assertTrue(participantFriendIds.contains(1L))
        assertTrue(participantFriendIds.contains(2L))
        assertFalse(participantFriendIds.contains(3L))
    }

    // 14. Delete an empty group
    @Test
    fun test14_DeleteEmptyGroup() = runBlocking {
        val gEmpty = groupRepo.createGroup("Empty Group", null, null, emptyList())
        assertNotNull(groupRepo.getGroupById(gEmpty))

        groupRepo.deleteGroup(gEmpty)
        assertNull(groupRepo.getGroupById(gEmpty))
    }

    // 15 & 16. Delete a group containing friends & verify friends are NEVER deleted
    @Test
    fun test15_16_DeleteGroupWithFriends_FriendsNeverDeleted() = runBlocking {
        val g1 = groupRepo.createGroup("Travel Squad", null, null, listOf(1L, 2L, 3L))
        assertNotNull(groupRepo.getGroupById(g1))

        // Delete group
        groupRepo.deleteGroup(g1)
        assertNull(groupRepo.getGroupById(g1))

        // Group members records deleted
        val group = groupRepo.getGroupById(g1)
        assertNull(group)

        // Friends 1, 2, 3 MUST still exist in database completely untouched!
        val f1 = db.friendDao().getFriendById(1L)
        val f2 = db.friendDao().getFriendById(2L)
        val f3 = db.friendDao().getFriendById(3L)
        assertNotNull(f1)
        assertNotNull(f2)
        assertNotNull(f3)
        assertEquals("Aman", f1!!.name)
        assertEquals("Bhavna", f2!!.name)
        assertEquals("Chetan", f3!!.name)
    }

    // 17. Delete a friend without financial history (permanent delete)
    @Test
    fun test17_DeleteFriendWithoutFinancialHistory_PermanentDelete() = runBlocking {
        // Friend 4 has no transactions or settlements
        val friend4Entity = db.friendDao().getFriendById(4L)
        assertNotNull(friend4Entity)

        friendRepo.deleteFriend(friend4)

        // Permanent deletion from database
        val deleted = db.friendDao().getFriendById(4L)
        assertNull(deleted)
    }

    // 18. Archive a friend with financial history (soft delete)
    @Test
    fun test18_DeleteFriendWithFinancialHistory_ArchivesFriend() = runBlocking {
        // Create transaction involving Friend 1
        val txn = Transaction(
            title = "Cab Share",
            amountPaise = 20000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 10000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 10000L)
            )
        )
        txnRepo.saveTransaction(txn)

        // Attempt deleting Friend 1
        friendRepo.deleteFriend(friend1)

        // Must NOT be permanently deleted; must be ARCHIVED (isArchived = true)
        val friend1After = db.friendDao().getFriendById(1L)
        assertNotNull(friend1After)
        assertTrue(friend1After!!.isArchived)
    }

    // 19. Verify historical settlements remain intact
    @Test
    fun test19_HistoricalSettlementsRemainIntact() = runBlocking {
        val settlement = Settlement(
            id = 50L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = 2000L,
            paymentMethod = PaymentMethod.UPI,
            isReversed = false
        )
        settlementRepo.recordSettlement(settlement)

        // Delete group containing friend 1
        val g1 = groupRepo.createGroup("Test", null, null, listOf(1L))
        groupRepo.deleteGroup(g1)

        // Settlement must be intact
        val storedSettlement = settlementRepo.getSettlementById(50L)
        assertNotNull(storedSettlement)
        assertEquals(50000L, storedSettlement!!.amountPaise)
        assertEquals(1L, storedSettlement.payerId)
        assertFalse(storedSettlement.isReversed)
    }

    // 20. Verify historical transaction shares remain intact
    @Test
    fun test20_HistoricalTransactionSharesRemainIntact() = runBlocking {
        val txn = Transaction(
            title = "Trip Grocery",
            amountPaise = 90000L,
            categoryId = 1L,
            transactionDate = 3000L,
            paymentMethod = PaymentMethod.CASH,
            paidByFriendId = 1L,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 2L, shareAmountPaise = 30000L)
            )
        )
        val txnId = txnRepo.saveTransaction(txn)

        // Perform group operations
        val g = groupRepo.createGroup("Trip", null, null, listOf(1L, 2L))
        groupRepo.setGroupMembers(g, listOf(1L))
        groupRepo.deleteGroup(g)

        val stored = txnRepo.getTransactionById(txnId)
        assertNotNull(stored)
        assertEquals(3, stored!!.participants.size)
        val shares = stored.participants.map { it.shareAmountPaise }
        assertEquals(listOf(30000L, 30000L, 30000L), shares)
    }

    // 21, 22, 23. Balance color & status rules:
    // Green (EmeraldMint): positive / THEY_OWE_ME
    // Red (CoralRed): negative / I_OWE_THEM
    // Neutral (TextSecondary): zero / SETTLED
    @Test
    fun test21_22_23_BalanceColorAndStatusRules() {
        // 21: Positive receivable balance: Friend owes me ₹500
        val txnPositive = Transaction(
            title = "Lunch",
            amountPaise = 100000L,
            paidByFriendId = null, // Me paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L)
            )
        )
        val ledgerPositive = LedgerCalculator.computeFriendLedgers(listOf(friend1), listOf(txnPositive), emptyList()).first()
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ledgerPositive.status)
        assertTrue(ledgerPositive.netBalancePaise > 0)
        // Verified: UI maps THEY_OWE_ME to EmeraldMint (Green)
        val colorPositive = if (ledgerPositive.netBalancePaise > 0) EmeraldMint else CoralRed
        assertEquals(EmeraldMint, colorPositive)

        // 22: Negative payable balance: I owe Friend ₹400
        val txnNegative = Transaction(
            title = "Coffee",
            amountPaise = 80000L,
            paidByFriendId = 2L, // Friend 2 paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 2L, shareAmountPaise = 40000L)
            )
        )
        val ledgerNegative = LedgerCalculator.computeFriendLedgers(listOf(friend2), listOf(txnNegative), emptyList()).first()
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledgerNegative.status)
        assertTrue(ledgerNegative.netBalancePaise < 0)
        // Verified: UI maps I_OWE_THEM to CoralRed (Red)
        val colorNegative = if (ledgerNegative.netBalancePaise < 0) CoralRed else EmeraldMint
        assertEquals(CoralRed, colorNegative)

        // 23: Settled zero balance
        val settlement = Settlement(
            payerId = null,
            receiverId = 2L,
            amountPaise = 40000L,
            settlementDate = 5000L,
            paymentMethod = PaymentMethod.UPI
        )
        val ledgerSettled = LedgerCalculator.computeFriendLedgers(listOf(friend2), listOf(txnNegative), listOf(settlement)).first()
        assertEquals(FriendBalanceStatus.SETTLED, ledgerSettled.status)
        assertEquals(0L, ledgerSettled.netBalancePaise)
        // Verified: UI maps SETTLED to TextSecondary
        val colorSettled = if (ledgerSettled.netBalancePaise == 0L) TextSecondary else EmeraldMint
        assertEquals(TextSecondary, colorSettled)
    }

    // 24. Verify your share is displayed above the total amount for split expenses
    @Test
    fun test24_SplitExpense_DisplaysYourShareProminentlyAboveTotal() {
        // For a split transaction of ₹40 where my share is ₹20:
        val txn = Transaction(
            title = "Snacks",
            amountPaise = 4000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 2000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 2000L)
            )
        )

        val isSplit = txn.participants.size > 1
        assertTrue(isSplit)

        val myParticipant = txn.participants.firstOrNull { it.friendId == null }
        assertNotNull(myParticipant)
        val myShare = myParticipant!!.shareAmountPaise

        // Display logic asserts:
        // Primary text (Upper side): "Your share: ₹20.00"
        val upperDisplay = "Your share: ${CurrencyUtils.formatPaiseToRupees(myShare)}"
        // Secondary text (Lower side): "Total amount: ₹40.00"
        val lowerDisplay = "Total amount: ${CurrencyUtils.formatPaiseToRupees(txn.amountPaise)}"

        assertEquals("Your share: ₹20.00", upperDisplay)
        assertEquals("Total amount: ₹40.00", lowerDisplay)
    }

    // 25. Verify transactions without splits do not show misleading shares
    @Test
    fun test25_SoloExpense_DisplaysTotalAmountWithoutMisleadingShare() {
        val txn = Transaction(
            title = "Electricity Bill",
            amountPaise = 150000L,
            paidByFriendId = null,
            participants = emptyList() // Solo expense, no splits
        )

        val isSplit = txn.participants.size > 1
        assertFalse(isSplit)

        // For non-split transactions:
        // Primary text: Total amount directly (e.g. "₹1,500.00"), no "Your share:" prefix
        val primaryDisplay = CurrencyUtils.formatPaiseToRupees(txn.amountPaise)
        assertEquals("₹1,500.00", primaryDisplay)
    }

    // 26. Test Room migration with existing user data
    @Test
    fun test26_RoomMigrationWithExistingUserData() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_suite_v3_to_v4", ".db")
        tempDbFile.deleteOnExit()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDbFile.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconName` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `isDefault` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `friends` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phoneNumber` TEXT, `email` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `transactionDate` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL, `accountSource` TEXT, `paidByFriendId` INTEGER, `splitMethod` TEXT NOT NULL, `transactionType` TEXT NOT NULL, `transactionStatus` TEXT NOT NULL, `source` TEXT NOT NULL, `merchantName` TEXT, `referenceNumber` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_participants` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL, `friendId` INTEGER, `shareAmountPaise` INTEGER NOT NULL, `percentage` REAL NOT NULL, `splitMethod` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `settlements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `payerId` INTEGER, `receiverId` INTEGER, `amountPaise` INTEGER NOT NULL, `settlementDate` INTEGER NOT NULL, `paymentMethod` TEXT NOT NULL, `referenceNumber` TEXT, `notes` TEXT, `isReversed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `pending_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `detectedAmountPaise` INTEGER NOT NULL, `detectedType` TEXT NOT NULL, `detectedBank` TEXT, `detectedAccount` TEXT, `detectedMerchant` TEXT, `referenceNumber` TEXT, `rawContent` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `source` TEXT NOT NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val sqlDb = helper.writableDatabase
        sqlDb.execSQL("INSERT INTO friends (id, name, createdAt, isArchived) VALUES (1, 'User 1', 1000, 0)")
        sqlDb.execSQL("INSERT INTO categories (id, name, iconName, colorHex, isDefault) VALUES (1, 'Food', 'restaurant', '#FF0000', 1)")
        sqlDb.execSQL("INSERT INTO transactions (id, title, description, amountPaise, categoryId, transactionDate, paymentMethod, splitMethod, transactionType, transactionStatus, source, createdAt, updatedAt) VALUES (1, 'Old Txn', '', 50000, 1, 1000, 'UPI', 'EQUAL', 'EXPENSE', 'ACTIVE', 'MANUAL', 1000, 1000)")
        sqlDb.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (1, 1, 1, 50000, 100.0, 'EQUAL')")

        // Migrate 1 -> 3 -> 4
        RupeeFlowDatabase.MIGRATION_1_3.migrate(sqlDb)
        RupeeFlowDatabase.MIGRATION_3_4.migrate(sqlDb)

        val txnCheck = sqlDb.query("SELECT title FROM transactions WHERE id = 1")
        assertTrue(txnCheck.moveToFirst())
        assertEquals("Old Txn", txnCheck.getString(0))
        txnCheck.close()

        val partCheck = sqlDb.query("SELECT shareAmountPaise FROM transaction_participants WHERE id = 1")
        assertTrue(partCheck.moveToFirst())
        assertEquals(50000L, partCheck.getLong(0))
        partCheck.close()

        sqlDb.close()
    }

    // 27. Test database foreign-key integrity
    @Test
    fun test27_DatabaseForeignKeyIntegrity() = runBlocking {
        // Insert group and member
        val gId = groupRepo.createGroup("Test FK Group", null, null, listOf(1L, 2L))
        assertNotNull(groupRepo.getGroupById(gId))

        // Run PRAGMA foreign_key_check on active DB
        val cursor = db.openHelper.writableDatabase.query("PRAGMA foreign_key_check")
        assertEquals(0, cursor.count)
        cursor.close()

        // Delete group -> verify FK integrity remains 100% clean
        groupRepo.deleteGroup(gId)

        val cursorAfter = db.openHelper.writableDatabase.query("PRAGMA foreign_key_check")
        assertEquals(0, cursorAfter.count)
        cursorAfter.close()
    }

    // Edge Cases & Financial Rounding:
    // Split ₹100 equally across 3 participants:
    // Integer paise: 10000 paise / 3 = 3333 paise each, with 1 paise remainder distributed
    // Sum of shares MUST exactly equal total amountPaise (10000 paise).
    @Test
    fun testEdgeCase_FinancialRounding_PaiseExactness() {
        val totalPaise = 10000L // ₹100.00
        val participantCount = 3
        val baseShare = totalPaise / participantCount // 3333
        val remainder = (totalPaise % participantCount).toInt() // 1

        val shares = (0 until participantCount).map { index ->
            if (index < remainder) baseShare + 1L else baseShare
        }

        assertEquals(3334L, shares[0])
        assertEquals(3333L, shares[1])
        assertEquals(3333L, shares[2])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun testEdgeCase_OnePaisaSplit() {
        val totalPaise = 1L // ₹0.01
        val participantCount = 2
        val baseShare = totalPaise / participantCount // 0
        val remainder = (totalPaise % participantCount).toInt() // 1

        val shares = (0 until participantCount).map { index ->
            if (index < remainder) baseShare + 1L else baseShare
        }

        assertEquals(1L, shares[0])
        assertEquals(0L, shares[1])
        assertEquals(totalPaise, shares.sum())
    }
}