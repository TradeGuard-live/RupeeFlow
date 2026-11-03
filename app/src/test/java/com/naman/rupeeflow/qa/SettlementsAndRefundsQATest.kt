package com.naman.rupeeflow.qa

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.SettlementRepositoryImpl
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * RupeeFlow Production QA Test Plan
 * Section 5: Settlements and refund accounting — TC049 to TC058
 */
@RunWith(RobolectricTestRunner::class)
class SettlementsAndRefundsQATest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var settlementRepository: SettlementRepository

    private val friendA = Friend(id = 101L, name = "Friend A")

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        settlementRepository = SettlementRepositoryImpl(
            settlementDao = database.settlementDao(),
            friendDao = database.friendDao()
        )

        runBlocking {
            database.friendDao().insertFriend(FriendEntity(id = 101L, name = "Friend A", createdAt = 1000L))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test_TC049_RecordFriendToMeSettlement() = runBlocking {
        // Steps: A owes Me ₹500. Record A paying Me ₹300.
        val txn = Transaction(title = "Lunch", amountPaise = 50000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)))
        val settlement = Settlement(payerId = 101L, receiverId = null, amountPaise = 30000L)
        val sId = settlementRepository.recordSettlement(settlement)
        assertTrue(sId > 0L)

        // Expected: A's outstanding balance becomes ₹200.
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(settlement))
        val ledgerA = ledgers.first { it.id == 101L }
        assertEquals(20000L, ledgerA.totalTheyOweMePaise)
        assertEquals(20000L, ledgerA.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ledgerA.status)
    }

    @Test
    fun test_TC050_RecordMeToFriendSettlement() = runBlocking {
        // Steps: Me owes A ₹500. Record Me paying A ₹300.
        val txn = Transaction(title = "Dinner", amountPaise = 50000L, paidByFriendId = 101L, participants = listOf(TransactionParticipant(friendId = null, shareAmountPaise = 50000L)))
        val settlement = Settlement(payerId = null, receiverId = 101L, amountPaise = 30000L) // Me paid A ₹300
        val sId = settlementRepository.recordSettlement(settlement)
        assertTrue(sId > 0L)

        // Expected: Me's outstanding balance becomes ₹200.
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(settlement))
        val ledgerA = ledgers.first { it.id == 101L }
        assertEquals(20000L, ledgerA.totalIOweThemPaise)
        assertEquals(-20000L, ledgerA.netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledgerA.status)
    }

    @Test
    fun test_TC051_EditSettlementAmount() = runBlocking {
        // Steps: Edit an existing ₹300 settlement to ₹400.
        val txn = Transaction(title = "Movie", amountPaise = 50000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)))
        val settlement = Settlement(payerId = 101L, receiverId = null, amountPaise = 30000L)
        val sId = settlementRepository.recordSettlement(settlement)

        val retrieved = settlementRepository.getSettlementById(sId)!!
        val edited = retrieved.copy(amountPaise = 40000L)
        settlementRepository.updateSettlement(edited)

        // Expected: Ledger reflects ₹400, not ₹700. No second settlement is inserted.
        val allSettlements = settlementRepository.getAllSettlementsList()
        assertEquals(1, allSettlements.size)
        assertEquals(40000L, allSettlements.first().amountPaise)

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), allSettlements)
        assertEquals(10000L, ledgers.first().totalTheyOweMePaise)
    }

    @Test
    fun test_TC052_EditSettlementDirection() = runBlocking {
        // Steps: Change the payer and receiver. Originally A paid Me ₹300. Change to Me paid A ₹300.
        val sId = settlementRepository.recordSettlement(Settlement(payerId = 101L, receiverId = null, amountPaise = 30000L))
        val retrieved = settlementRepository.getSettlementById(sId)!!

        val edited = retrieved.copy(payerId = null, receiverId = 101L)
        settlementRepository.updateSettlement(edited)

        // Expected: Direction is recalculated correctly and old direction effects are removed.
        val after = settlementRepository.getSettlementById(sId)!!
        assertNull(after.payerId)
        assertEquals(101L, after.receiverId)

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), emptyList(), listOf(after))
        val ledgerA = ledgers.first()
        // Me paid A ₹300 without debts means A owes Me ₹300
        assertEquals(30000L, ledgerA.totalTheyOweMePaise)
    }

    @Test
    fun test_TC053_ReverseSettlement() = runBlocking {
        // Steps: Reverse an active settlement.
        val txn = Transaction(title = "Cab", amountPaise = 50000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)))
        val sId = settlementRepository.recordSettlement(Settlement(payerId = 101L, receiverId = null, amountPaise = 50000L))

        settlementRepository.reverseSettlement(sId)

        // Expected: Its ledger effect is removed.
        val reversed = settlementRepository.getSettlementById(sId)!!
        assertTrue(reversed.isReversed)

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(reversed))
        assertEquals(50000L, ledgers.first().totalTheyOweMePaise) // Remains ₹500 owed since settlement is reversed
    }

    @Test
    fun test_TC054_RestoreReversedSettlement() = runBlocking {
        // Steps: Unreverse the settlement.
        val txn = Transaction(title = "Cab", amountPaise = 50000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)))
        val sId = settlementRepository.recordSettlement(Settlement(payerId = 101L, receiverId = null, amountPaise = 50000L, isReversed = true))

        settlementRepository.unreverseSettlement(sId)

        // Expected: Its original effect returns exactly once.
        val restored = settlementRepository.getSettlementById(sId)!!
        assertFalse(restored.isReversed)

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(restored))
        assertEquals(0L, ledgers.first().totalTheyOweMePaise) // Settled!
        assertEquals(FriendBalanceStatus.SETTLED, ledgers.first().status)
    }

    @Test
    fun test_TC055_EditReversedSettlement() = runBlocking {
        // Steps: Edit a reversed settlement.
        val sId = settlementRepository.recordSettlement(Settlement(payerId = 101L, receiverId = null, amountPaise = 50000L, isReversed = true))
        val reversed = settlementRepository.getSettlementById(sId)!!

        val edited = reversed.copy(notes = "Updated note", amountPaise = 60000L)
        settlementRepository.updateSettlement(edited)

        // Expected: Reversal status remains preserved. Editing does not accidentally activate it.
        val retrieved = settlementRepository.getSettlementById(sId)!!
        assertTrue(retrieved.isReversed)
        assertEquals(60000L, retrieved.amountPaise)

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), emptyList(), listOf(retrieved))
        assertEquals(0L, ledgers.first().totalTheyOweMePaise)
    }

    @Test
    fun test_TC056_DeleteSettlement() = runBlocking {
        // Steps: Delete a settlement that has affected a ledger.
        val txn = Transaction(title = "Lunch", amountPaise = 50000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)))
        val sId = settlementRepository.recordSettlement(Settlement(payerId = 101L, receiverId = null, amountPaise = 50000L))
        val toDelete = settlementRepository.getSettlementById(sId)!!

        settlementRepository.deleteSettlement(toDelete)

        // Expected: Settlement is removed and financial effect recalculated.
        assertNull(settlementRepository.getSettlementById(sId))
        val allSettlements = settlementRepository.getAllSettlementsList()
        assertTrue(allSettlements.isEmpty())

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), allSettlements)
        assertEquals(50000L, ledgers.first().totalTheyOweMePaise)
    }

    @Test
    fun test_TC057_InvalidSettlementValidation() = runBlocking {
        // Steps: Try zero amount, negative amount, same payer and receiver, or both sides set to Me.
        val valid = Settlement(payerId = 101L, receiverId = null, amountPaise = 10000L)
        val sId = settlementRepository.recordSettlement(valid)

        // Zero amount
        try {
            settlementRepository.updateSettlement(valid.copy(id = sId, amountPaise = 0L))
            fail("Expected exception for 0 amount")
        } catch (_: IllegalArgumentException) {}

        // Negative amount
        try {
            settlementRepository.updateSettlement(valid.copy(id = sId, amountPaise = -100L))
            fail("Expected exception for negative amount")
        } catch (_: IllegalArgumentException) {}

        // Same payer and receiver
        try {
            settlementRepository.updateSettlement(valid.copy(id = sId, payerId = 101L, receiverId = 101L))
            fail("Expected exception for payer == receiver")
        } catch (_: IllegalArgumentException) {}

        // Both sides Me
        try {
            settlementRepository.updateSettlement(valid.copy(id = sId, payerId = null, receiverId = null))
            fail("Expected exception for payer == null && receiver == null")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun test_TC058_RefundAndSettlementInteraction() {
        // Steps: Create a shared expense (₹1,000 paid by Me, Friend A's share ₹500).
        val initialTxn = Transaction(
            id = 1L,
            title = "Shared Shopping",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        // Apply partial refund: item returned, ₹200 credited back
        val refundTxn = Transaction(
            id = 2L,
            title = "Return Refund",
            amountPaise = 20000L,
            transactionType = TransactionType.REFUND
        )
        // Record settlement: A pays Me ₹300
        val settlement = Settlement(
            id = 1L,
            payerId = 101L,
            receiverId = null,
            amountPaise = 30000L
        )

        // Expected: Refund and settlement effects are applied independently and exactly once.
        // Net personal spending: Me's share (500) - refund (200) = ₹300 (30,000 paise)
        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = listOf(initialTxn, refundTxn),
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = 0L,
            now = System.currentTimeMillis() + 10000L
        )
        assertEquals(30000L, summary.netSpendingPaise)

        // Friend ledger: 500 - 300 = ₹200 remaining owed by A
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(initialTxn), listOf(settlement))
        assertEquals(20000L, ledgers.first().totalTheyOweMePaise)
    }
}
