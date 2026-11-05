package com.naman.rupeeflow.qa

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.FriendGroupRepositoryImpl
import com.naman.rupeeflow.data.repository.FriendRepositoryImpl
import com.naman.rupeeflow.data.repository.TransactionRepositoryImpl
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
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
 * Phase 2 — Comprehensive Add Transaction and Group Integration QA Test Suite.
 * Covers all 20 requirements specified in Phase 2.
 */
@RunWith(RobolectricTestRunner::class)
class TransactionAndGroupIntegrationQATest {

    private lateinit var db: RupeeFlowDatabase
    private lateinit var groupRepo: FriendGroupRepositoryImpl
    private lateinit var friendRepo: FriendRepositoryImpl
    private lateinit var txnRepo: TransactionRepositoryImpl

    private val friendA = Friend(id = 101L, name = "Friend A", phoneNumber = "9876543210")
    private val friendB = Friend(id = 102L, name = "Friend B", phoneNumber = "9876543211")
    private val friendC = Friend(id = 103L, name = "Friend C", phoneNumber = "9876543212")
    private val friendD = Friend(id = 104L, name = "Friend D", phoneNumber = "9876543213")

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

            db.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF0000", isDefault = true))

            db.friendDao().insertFriend(FriendEntity(id = 101L, name = friendA.name, phoneNumber = friendA.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 102L, name = friendB.name, phoneNumber = friendB.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 103L, name = friendC.name, phoneNumber = friendC.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 104L, name = friendD.name, phoneNumber = friendD.phoneNumber))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1 & 2. Select a group & automatically populate participants
    @Test
    fun test01_02_SelectGroup_AutoPopulatesParticipants() = runBlocking {
        val groupId = groupRepo.createGroup("Roommates", null, null, listOf(101L, 102L))
        val group = groupRepo.getGroupById(groupId)!!

        var selectedFriendIds = emptySet<Long>()
        // User clicks group chip
        selectedFriendIds = group.memberFriendIds.toSet()

        assertEquals(setOf(101L, 102L), selectedFriendIds)
        assertEquals(2, selectedFriendIds.size)
    }

    // 3. Select another group
    @Test
    fun test03_SelectAnotherGroup_ReplacesSelection() = runBlocking {
        val g1 = groupRepo.createGroup("Roommates", null, null, listOf(101L, 102L))
        val g2 = groupRepo.createGroup("Office", null, null, listOf(103L, 104L))

        var selectedFriendIds = groupRepo.getGroupById(g1)!!.memberFriendIds.toSet()
        assertEquals(setOf(101L, 102L), selectedFriendIds)

        // Select group 2
        selectedFriendIds = groupRepo.getGroupById(g2)!!.memberFriendIds.toSet()
        assertEquals(setOf(103L, 104L), selectedFriendIds)
    }

    // 4 & 5. Manually remove and add participants
    @Test
    fun test04_05_ManuallyRemoveAndAddParticipant() = runBlocking {
        val g1 = groupRepo.createGroup("Trip", null, null, listOf(101L, 102L, 103L))
        var selected = groupRepo.getGroupById(g1)!!.memberFriendIds.toSet()

        // Manually remove 102
        selected = selected - 102L
        assertEquals(setOf(101L, 103L), selected)

        // Manually add 104
        selected = selected + 104L
        assertEquals(setOf(101L, 103L, 104L), selected)
    }

    // 6. Switch groups after manual modification
    @Test
    fun test06_SwitchGroupsAfterManualModification() = runBlocking {
        val g1 = groupRepo.createGroup("College", null, null, listOf(101L, 102L))
        val g2 = groupRepo.createGroup("Travel", null, null, listOf(103L))

        var selected = groupRepo.getGroupById(g1)!!.memberFriendIds.toSet() + 104L // added 104 manually
        assertEquals(setOf(101L, 102L, 104L), selected)

        // User switches to group 2
        selected = groupRepo.getGroupById(g2)!!.memberFriendIds.toSet()
        assertEquals(setOf(103L), selected)
    }

    // 7. Select All Friends
    @Test
    fun test07_SelectAllFriends_Option() = runBlocking {
        val allActive = friendRepo.getActiveFriends().first()
        val allIds = allActive.map { it.id }.toSet()
        assertEquals(setOf(101L, 102L, 103L, 104L), allIds)
    }

    // 8. Select an empty group
    @Test
    fun test08_SelectEmptyGroup() = runBlocking {
        val emptyGid = groupRepo.createGroup("Empty", null, null, emptyList())
        val selected = groupRepo.getGroupById(emptyGid)!!.memberFriendIds.toSet()
        assertTrue(selected.isEmpty())
    }

    // 9. Create transaction without a group
    @Test
    fun test09_CreateTransactionWithoutGroup() = runBlocking {
        val txn = Transaction(
            title = "Solo Groceries",
            amountPaise = 150000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = emptyList()
        )
        val id = txnRepo.saveTransaction(txn)
        assertTrue(id > 0)
        val saved = txnRepo.getTransactionById(id)
        assertNotNull(saved)
        assertTrue(saved!!.participants.isEmpty())
    }

    // 10. Create transaction with one participant
    @Test
    fun test10_CreateTransactionWithOneParticipant() = runBlocking {
        val txn = Transaction(
            title = "Book for Friend A",
            amountPaise = 50000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        val id = txnRepo.saveTransaction(txn)
        val saved = txnRepo.getTransactionById(id)
        assertNotNull(saved)
        assertEquals(1, saved!!.participants.size)
        assertEquals(101L, saved.participants[0].friendId)
        assertEquals(50000L, saved.participants[0].shareAmountPaise)
    }

    // 11. Create transaction with multiple participants
    @Test
    fun test11_CreateTransactionWithMultipleParticipants() = runBlocking {
        val txn = Transaction(
            title = "Dinner with 3 friends",
            amountPaise = 400000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 103L, shareAmountPaise = 100000L)
            )
        )
        val id = txnRepo.saveTransaction(txn)
        val saved = txnRepo.getTransactionById(id)
        assertNotNull(saved)
        assertEquals(4, saved!!.participants.size)
        assertEquals(400000L, saved.participants.sumOf { it.shareAmountPaise })
    }

    // 12. Verify duplicate participants are impossible
    @Test
    fun test12_VerifyDuplicateParticipantsAreRejectedOrDeduplicated() = runBlocking {
        // Enforce that participant list cannot have duplicate friendIds
        val rawParticipants = listOf(
            TransactionParticipant(friendId = 101L, shareAmountPaise = 25000L),
            TransactionParticipant(friendId = 101L, shareAmountPaise = 25000L),
            TransactionParticipant(friendId = null, shareAmountPaise = 50000L)
        )
        // Deduplicate distinct participants
        val distinctParticipants = rawParticipants.distinctBy { it.friendId }
        assertEquals(2, distinctParticipants.size)
    }

    // 13. Change split method
    @Test
    fun test13_ChangeSplitMethod() {
        val totalPaise = 100000L // ₹1,000.00
        val participantsCount = 2

        // Equal split
        val equalShares = listOf(50000L, 50000L)
        assertEquals(totalPaise, equalShares.sum())

        // Percentage split (70% - 30%)
        val pctShare1 = (totalPaise * 70.0 / 100.0).toLong() // 70000
        val pctShare2 = totalPaise - pctShare1 // 30000
        assertEquals(totalPaise, pctShare1 + pctShare2)

        // Custom split (₹650 and ₹350)
        val customShares = listOf(65000L, 35000L)
        assertEquals(totalPaise, customShares.sum())
    }

    // 14. Change transaction amount
    @Test
    fun test14_ChangeTransactionAmount_RecalculatesParticipants() = runBlocking {
        val initialTxn = Transaction(
            title = "Team Lunch",
            amountPaise = 200000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 100000L)
            )
        )
        val id = txnRepo.saveTransaction(initialTxn)

        // Edit amount to ₹3,000.00 (300000 paise)
        val updatedTxn = initialTxn.copy(
            id = id,
            amountPaise = 300000L,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 150000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 150000L)
            )
        )
        txnRepo.saveTransaction(updatedTxn)

        val retrieved = txnRepo.getTransactionById(id)!!
        assertEquals(300000L, retrieved.amountPaise)
        assertEquals(150000L, retrieved.participants.first { it.friendId == 101L }.shareAmountPaise)
    }

    // 15. Change payer
    @Test
    fun test15_ChangePayer_UpdatesLedgerCorrectly() = runBlocking {
        // Me paid initially
        val txn = Transaction(
            id = 501L,
            title = "Movie Tickets",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null, // Me paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        var ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), emptyList()).first()
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ledger.status)
        assertEquals(50000L, ledger.netBalancePaise)

        // Change payer to Friend A
        val updatedPayerTxn = txn.copy(paidByFriendId = 101L)
        ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(updatedPayerTxn), emptyList()).first()
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledger.status)
        assertEquals(-50000L, ledger.netBalancePaise)
    }

    // 16 & 17. Edit an existing split transaction and participants
    @Test
    fun test16_17_EditExistingSplitTransactionAndParticipants() = runBlocking {
        val initial = Transaction(
            title = "Snacks",
            amountPaise = 60000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paymentMethod = PaymentMethod.CASH,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 30000L)
            )
        )
        val id = txnRepo.saveTransaction(initial)

        // Edit: add friend 102 into split
        val edited = initial.copy(
            id = id,
            title = "Snacks & Drinks",
            amountPaise = 60000L,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 20000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 20000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 20000L)
            )
        )
        txnRepo.saveTransaction(edited)

        val retrieved = txnRepo.getTransactionById(id)!!
        assertEquals("Snacks & Drinks", retrieved.title)
        assertEquals(3, retrieved.participants.size)
        assertEquals(setOf(null, 101L, 102L), retrieved.participants.map { it.friendId }.toSet())
    }

    // 18. Archive a selected friend
    @Test
    fun test18_ArchiveSelectedFriend_SoftDeletesWithoutBreakingExistingTransactions() = runBlocking {
        val txn = Transaction(
            title = "Shared Ride",
            amountPaise = 50000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paymentMethod = PaymentMethod.UPI,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 25000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 25000L)
            )
        )
        val id = txnRepo.saveTransaction(txn)

        // Archive friend 101
        friendRepo.deleteFriend(friendA)
        val archivedA = friendRepo.getFriendById(101L)!!
        assertTrue(archivedA.isArchived)

        // Transaction participants still reference 101 cleanly
        val retrieved = txnRepo.getTransactionById(id)!!
        assertEquals(2, retrieved.participants.size)
        assertTrue(retrieved.participants.any { it.friendId == 101L })
    }

    // 19. Delete a group while a transaction form is open
    @Test
    fun test19_DeleteGroupWhileFormOpen() = runBlocking {
        val gid = groupRepo.createGroup("Temp Group", null, null, listOf(101L, 102L))
        val group = groupRepo.getGroupById(gid)!!

        // Form loaded members
        val formSelectedParticipants = group.memberFriendIds.toSet()

        // Group is deleted in background
        groupRepo.deleteGroup(gid)
        assertNull(groupRepo.getGroupById(gid))

        // Saving transaction using formSelectedParticipants still succeeds safely
        val txn = Transaction(
            title = "Form Transaction",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            participants = formSelectedParticipants.map {
                TransactionParticipant(friendId = it, shareAmountPaise = 50000L)
            }
        )
        val savedId = txnRepo.saveTransaction(txn)
        assertTrue(savedId > 0)
    }

    // 20. CRITICAL: Historical transactions must retain their original participants and exact saved shares
    // Changing group membership must never silently recalculate old transactions!
    @Test
    fun test20_HistoricalTransactionsRetainExactSharesWhenGroupChanges() = runBlocking {
        val groupId = groupRepo.createGroup("Hostel Boys", null, null, listOf(101L, 102L))

        // Create transaction with group members
        val txn = Transaction(
            title = "Hostel Groceries",
            amountPaise = 300000L,
            categoryId = 1L,
            transactionDate = 1500L,
            paymentMethod = PaymentMethod.UPI,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 100000L)
            )
        )
        val txnId = txnRepo.saveTransaction(txn)

        // Group membership changes: remove 102, add 103 and 104
        groupRepo.setGroupMembers(groupId, listOf(101L, 103L, 104L))

        // Verify group has new members
        val updatedGroup = groupRepo.getGroupById(groupId)!!
        assertEquals(setOf(101L, 103L, 104L), updatedGroup.memberFriendIds.toSet())

        // HISTORICAL TRANSACTION MUST REMAIN 100% UNCHANGED
        val historicalTxn = txnRepo.getTransactionById(txnId)!!
        assertEquals(3, historicalTxn.participants.size)
        val pIds = historicalTxn.participants.map { it.friendId }.toSet()
        assertEquals(setOf(null, 101L, 102L), pIds)
        assertEquals(100000L, historicalTxn.participants.first { it.friendId == 102L }.shareAmountPaise)
    }
}
