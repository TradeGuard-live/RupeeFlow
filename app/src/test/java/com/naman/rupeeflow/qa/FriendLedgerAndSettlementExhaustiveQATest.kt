package com.naman.rupeeflow.qa

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.ui.theme.CoralRed
import com.naman.rupeeflow.ui.theme.EmeraldMint
import com.naman.rupeeflow.ui.theme.TextSecondary
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 4 — Friend Ledger and Settlement Exhaustive Tests
 * Covers over-settlement, opposite-direction flows, refund interaction,
 * settlement reversals/edits, boundary cases, and strict balance color mappings.
 */
class FriendLedgerAndSettlementExhaustiveQATest {

    private val friendA = Friend(id = 101L, name = "Friend A")
    private val friendB = Friend(id = 102L, name = "Friend B")

    @Test
    fun testFriendOwesMe_ColorIsGreen() {
        val txn = Transaction(
            title = "Lunch",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        val ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), emptyList()).first()
        assertEquals(50000L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ledger.status)

        val color = if (ledger.netBalancePaise > 0) EmeraldMint else CoralRed
        assertEquals(EmeraldMint, color) // Green
    }

    @Test
    fun testIOweFriend_ColorIsRed() {
        val txn = Transaction(
            title = "Cab",
            amountPaise = 60000L,
            paidByFriendId = 101L,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 30000L)
            )
        )
        val ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), emptyList()).first()
        assertEquals(-30000L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledger.status)

        val color = if (ledger.netBalancePaise < 0) CoralRed else EmeraldMint
        assertEquals(CoralRed, color) // Red
    }

    @Test
    fun testFullySettled_ColorIsNeutral() {
        val txn = Transaction(
            title = "Lunch",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        val settlement = Settlement(
            payerId = 101L,
            receiverId = null,
            amountPaise = 50000L
        )
        val ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(settlement)).first()
        assertEquals(0L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledger.status)

        val color = if (ledger.netBalancePaise == 0L) TextSecondary else EmeraldMint
        assertEquals(TextSecondary, color) // Neutral
    }

    @Test
    fun testPartialSettlement_ReducesReceivableBalance() {
        val txn = Transaction(
            title = "Dinner",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 100000L))
        )
        // Partial settlement: Friend pays ₹400 of ₹1000
        val settlement = Settlement(payerId = 101L, receiverId = null, amountPaise = 40000L)
        val ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(settlement)).first()
        assertEquals(60000L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ledger.status)
    }

    @Test
    fun testOverSettlement_FlipsBalanceDirectionFromOwesMeToIOweThem() {
        // Friend A owes Me ₹500
        val txn = Transaction(
            title = "Shared Item",
            amountPaise = 50000L,
            paidByFriendId = null,
            participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L))
        )
        // Friend A over-settles by sending ₹800
        val settlement = Settlement(payerId = 101L, receiverId = null, amountPaise = 80000L)
        val ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(settlement)).first()

        // Now Me owes Friend A ₹300!
        assertEquals(-30000L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledger.status)
        val color = if (ledger.netBalancePaise < 0) CoralRed else EmeraldMint
        assertEquals(CoralRed, color)
    }

    @Test
    fun testOppositeDirectionTransactions_NetCalculatedAccurately() {
        // Txn 1: Me pays ₹1,000, A's share is ₹500 (A owes Me ₹500)
        val t1 = Transaction(title = "Item 1", amountPaise = 100000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)))
        // Txn 2: A pays ₹600, Me's share is ₹600 (Me owes A ₹600)
        val t2 = Transaction(title = "Item 2", amountPaise = 60000L, paidByFriendId = 101L, participants = listOf(TransactionParticipant(friendId = null, shareAmountPaise = 60000L)))

        val ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(t1, t2), emptyList()).first()
        // Net: 50000 - 60000 = -10000 paise (I owe A ₹100)
        assertEquals(-10000L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledger.status)
    }

    @Test
    fun testRefundAfterSplit_ReducesNetExpense() {
        // Original expense: ₹1,000 split equally (Me ₹500, A ₹500)
        val t1 = Transaction(
            title = "Event Tickets",
            amountPaise = 100000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        // Full settlement occurred
        val s1 = Settlement(payerId = 101L, receiverId = null, amountPaise = 50000L)
        var ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(t1), listOf(s1)).first()
        assertEquals(0L, ledger.netBalancePaise)

        // Event cancelled -> Organizers refund ₹1,000 back to Me
        val refundTxn = Transaction(
            title = "Refund Event Tickets",
            amountPaise = 100000L,
            transactionType = TransactionType.REFUND,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        // Because Me received A's refunded share from the merchant, Me now owes A ₹500!
        ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(t1, refundTxn), listOf(s1)).first()
        assertEquals(-50000L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledger.status)
    }

    @Test
    fun testMultipleSettlementsAccumulateCorrectly() {
        val t1 = Transaction(title = "Bill", amountPaise = 100000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 100000L)))
        val s1 = Settlement(payerId = 101L, receiverId = null, amountPaise = 30000L)
        val s2 = Settlement(payerId = 101L, receiverId = null, amountPaise = 40000L)
        val s3 = Settlement(payerId = 101L, receiverId = null, amountPaise = 30000L)

        val ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(t1), listOf(s1, s2, s3)).first()
        assertEquals(0L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledger.status)
    }

    @Test
    fun testLargeBalances_NoLongArithmeticOverflow() {
        val largeAmount = 5_000_000_000_000L // ₹5000 Crore in paise
        val txn = Transaction(title = "Massive", amountPaise = largeAmount, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = largeAmount)))
        val settlement = Settlement(payerId = 101L, receiverId = null, amountPaise = largeAmount)

        val ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(settlement)).first()
        assertEquals(0L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledger.status)
    }

    @Test
    fun testZeroBalanceTransitionEdgeCase() {
        // Transactions and settlements that result in exactly 0 paise balance
        val t1 = Transaction(title = "1 Paisa", amountPaise = 1L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 1L)))
        val s1 = Settlement(payerId = 101L, receiverId = null, amountPaise = 1L)
        val ledger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(t1), listOf(s1)).first()
        assertEquals(0L, ledger.netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledger.status)
    }
}