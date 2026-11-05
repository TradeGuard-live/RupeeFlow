package com.naman.rupeeflow.qa

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/**
 * Phase 3 — Financial Engine and Split Tests (Exhaustive)
 * Covers Equal Splits, Percentage Splits, Custom Splits, Payer & Ownership,
 * and Property-Based Invariant Verification using strict Integer Paise arithmetic.
 */
class FinancialEngineExhaustiveQATest {

    private val friendA = Friend(id = 101L, name = "Friend A")
    private val friendB = Friend(id = 102L, name = "Friend B")
    private val friendC = Friend(id = 103L, name = "Friend C")

    // --- EQUAL SPLIT ENGINE TESTS ---

    private fun calculateEqualPaiseShares(totalPaise: Long, participantCount: Int): List<Long> {
        require(totalPaise > 0L) { "Amount must be positive" }
        require(participantCount > 0) { "Participant count must be positive" }
        val baseShare = totalPaise / participantCount
        val remainder = (totalPaise % participantCount).toInt()
        return (0 until participantCount).map { i ->
            if (i < remainder) baseShare + 1L else baseShare
        }
    }

    @Test
    fun testEqualSplit_100RupeesAmong2() {
        val totalPaise = 10000L // ₹100.00
        val shares = calculateEqualPaiseShares(totalPaise, 2)
        assertEquals(listOf(5000L, 5000L), shares)
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun testEqualSplit_100RupeesAmong3() {
        val totalPaise = 10000L // ₹100.00
        val shares = calculateEqualPaiseShares(totalPaise, 3)
        // 10000 / 3 = 3333 with remainder 1
        assertEquals(listOf(3334L, 3333L, 3333L), shares)
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun testEqualSplit_100RupeesAmong7() {
        val totalPaise = 10000L // ₹100.00
        val shares = calculateEqualPaiseShares(totalPaise, 7)
        // 10000 / 7 = 1428 with remainder 4
        // First 4 get 1429, remaining 3 get 1428
        assertEquals(1429L, shares[0])
        assertEquals(1429L, shares[1])
        assertEquals(1429L, shares[2])
        assertEquals(1429L, shares[3])
        assertEquals(1428L, shares[4])
        assertEquals(1428L, shares[5])
        assertEquals(1428L, shares[6])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun testEqualSplit_OneRupeeAmongMultipleParticipants() {
        val totalPaise = 100L // ₹1.00
        val shares = calculateEqualPaiseShares(totalPaise, 6)
        // 100 / 6 = 16 with remainder 4
        // 4 * 17 + 2 * 16 = 68 + 32 = 100
        assertEquals(17L, shares[0])
        assertEquals(17L, shares[1])
        assertEquals(17L, shares[2])
        assertEquals(17L, shares[3])
        assertEquals(16L, shares[4])
        assertEquals(16L, shares[5])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun testEqualSplit_OnePaiseAmongParticipants() {
        val totalPaise = 1L // ₹0.01
        val shares = calculateEqualPaiseShares(totalPaise, 3)
        // Remainder 1 goes to first participant, others get 0
        assertEquals(listOf(1L, 0L, 0L), shares)
        assertEquals(totalPaise, shares.sum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEqualSplit_ZeroAmount_Rejected() {
        calculateEqualPaiseShares(0L, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEqualSplit_NegativeAmount_Rejected() {
        calculateEqualPaiseShares(-5000L, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEqualSplit_EmptyParticipants_Rejected() {
        calculateEqualPaiseShares(10000L, 0)
    }

    @Test
    fun testEqualSplit_OneParticipant_TakesFullAmount() {
        val totalPaise = 50000L
        val shares = calculateEqualPaiseShares(totalPaise, 1)
        assertEquals(listOf(50000L), shares)
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun testEqualSplit_VeryLargeAmount_NoLongOverflow() {
        val totalPaise = 10_000_000_000_000L // ₹10,000 Crore in paise
        val shares = calculateEqualPaiseShares(totalPaise, 3)
        assertEquals(totalPaise, shares.sum())
        assertTrue(shares[0] > 0)
    }

    // --- PERCENTAGE SPLIT ENGINE TESTS ---

    private fun calculatePercentagePaiseShares(totalPaise: Long, percentages: List<Double>): List<Long> {
        val pctSum = percentages.sum()
        require(Math.abs(pctSum - 100.0) < 0.001) { "Percentages must sum to exactly 100%, got $pctSum%" }
        require(percentages.all { it >= 0.0 }) { "Percentages must be non-negative" }

        val rawShares = percentages.map { pct ->
            Math.round(totalPaise * pct / 100.0)
        }
        val diff = totalPaise - rawShares.sum()
        // Distribute rounding difference to the largest share
        val maxIndex = percentages.indexOf(percentages.maxOrNull() ?: 0.0)
        return rawShares.mapIndexed { i, share ->
            if (i == maxIndex) share + diff else share
        }
    }

    @Test
    fun testPercentageSplit_100PercentSingleAllocation() {
        val total = 10000L
        val shares = calculatePercentagePaiseShares(total, listOf(100.0))
        assertEquals(listOf(10000L), shares)
    }

    @Test
    fun testPercentageSplit_MultipleEqualPercentages() {
        val total = 10000L
        val shares = calculatePercentagePaiseShares(total, listOf(50.0, 50.0))
        assertEquals(listOf(5000L, 5000L), shares)
        assertEquals(total, shares.sum())
    }

    @Test
    fun testPercentageSplit_DecimalPercentages() {
        val total = 80000L // ₹800.00
        val shares = calculatePercentagePaiseShares(total, listOf(12.5, 37.5, 50.0))
        assertEquals(listOf(10000L, 30000L, 40000L), shares)
        assertEquals(total, shares.sum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPercentageSplit_UnderAllocation99_99_Rejected() {
        calculatePercentagePaiseShares(10000L, listOf(50.0, 49.99))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPercentageSplit_OverAllocation100_01_Rejected() {
        calculatePercentagePaiseShares(10000L, listOf(50.0, 50.01))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPercentageSplit_NegativePercentage_Rejected() {
        calculatePercentagePaiseShares(10000L, listOf(120.0, -20.0))
    }

    @Test
    fun testPercentageSplit_RoundingAndOnePaiseExactness() {
        val total = 10000L
        val shares = calculatePercentagePaiseShares(total, listOf(33.33, 33.33, 33.34))
        assertEquals(total, shares.sum())
    }

    // --- CUSTOM SPLIT ENGINE TESTS ---

    private fun validateCustomPaiseShares(totalPaise: Long, customShares: List<Long>) {
        require(totalPaise > 0L) { "Total must be positive" }
        require(customShares.isNotEmpty()) { "Custom shares cannot be empty" }
        require(customShares.all { it >= 0L }) { "Individual shares cannot be negative" }
        val sum = customShares.sum()
        require(sum == totalPaise) { "Sum of custom shares ($sum paise) must exactly equal total ($totalPaise paise)" }
    }

    @Test
    fun testCustomSplit_ExactAllocation() {
        val total = 15000L
        val shares = listOf(6000L, 4000L, 5000L)
        validateCustomPaiseShares(total, shares)
        assertEquals(total, shares.sum())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCustomSplit_UnderAllocation_Rejected() {
        validateCustomPaiseShares(10000L, listOf(4000L, 5000L)) // 9000 != 10000
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCustomSplit_OverAllocation_Rejected() {
        validateCustomPaiseShares(10000L, listOf(6000L, 5000L)) // 11000 != 10000
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCustomSplit_NegativeShare_Rejected() {
        validateCustomPaiseShares(10000L, listOf(12000L, -2000L))
    }

    @Test
    fun testCustomSplit_ZeroShareAllowedForNonPayingObserver() {
        val total = 10000L
        val shares = listOf(10000L, 0L)
        validateCustomPaiseShares(total, shares)
        assertEquals(total, shares.sum())
    }

    // --- PAYER AND OWNERSHIP TESTS ---

    @Test
    fun testPayerAndOwnership_MePaysAll() {
        // Me pays ₹1,200. Friend A owes ₹400, Friend B owes ₹400, Me owes ₹400
        val txn = Transaction(
            title = "Dinner",
            amountPaise = 120000L,
            paidByFriendId = null, // Me paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 40000L)
            )
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA, friendB), listOf(txn), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }
        val ledgerB = ledgers.first { it.id == 102L }

        assertEquals(40000L, ledgerA.totalTheyOweMePaise)
        assertEquals(0L, ledgerA.totalIOweThemPaise)
        assertEquals(40000L, ledgerA.netBalancePaise)

        assertEquals(40000L, ledgerB.totalTheyOweMePaise)
        assertEquals(40000L, ledgerB.netBalancePaise)
    }

    @Test
    fun testPayerAndOwnership_FriendPaysAllForMe() {
        // Friend A pays ₹1,000 for Me (solo friend expense)
        val txn = Transaction(
            title = "Flight Ticket",
            amountPaise = 100000L,
            paidByFriendId = 101L, // Friend A paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L)
            )
        )
        val ledgerA = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), emptyList()).first()
        assertEquals(100000L, ledgerA.totalIOweThemPaise)
        assertEquals(0L, ledgerA.totalTheyOweMePaise)
        assertEquals(-100000L, ledgerA.netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledgerA.status)
    }

    @Test
    fun testPayerAndOwnership_FriendPaysAndParticipantsSplit() {
        // Friend A pays ₹900. Shares: Me ₹300, Friend A ₹300, Friend B ₹300.
        // Between Me and Friend A: Me owes Friend A ₹300.
        val txn = Transaction(
            title = "Cab Ride",
            amountPaise = 90000L,
            paidByFriendId = 101L,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 30000L)
            )
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA, friendB), listOf(txn), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }
        val ledgerB = ledgers.first { it.id == 102L }

        // Me owes Friend A ₹300
        assertEquals(30000L, ledgerA.totalIOweThemPaise)
        assertEquals(-30000L, ledgerA.netBalancePaise)
        // Friend B owes Friend A ₹300, but between Me and B, balance is 0
        assertEquals(0L, ledgerB.netBalancePaise)
    }

    @Test
    fun testPayerAndOwnership_PayerIsNotAParticipant() {
        // Me pays ₹500 as a gift split between Friend A and Friend B (Me share is 0)
        val txn = Transaction(
            title = "Gift for Friends",
            amountPaise = 50000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = 101L, shareAmountPaise = 25000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 25000L)
            )
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA, friendB), listOf(txn), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }
        val ledgerB = ledgers.first { it.id == 102L }

        assertEquals(25000L, ledgerA.totalTheyOweMePaise)
        assertEquals(25000L, ledgerB.totalTheyOweMePaise)
    }

    // --- PROPERTY-BASED / RANDOMIZED TESTS ---

    @Test
    fun testPropertyBased_EqualSplitConservationOfPaise() {
        val random = Random(42)
        for (i in 1..100) {
            val totalPaise = random.nextLong(1L, 10_000_000L) // up to ₹1,00,000.00
            val participantCount = random.nextInt(1, 20)
            val shares = calculateEqualPaiseShares(totalPaise, participantCount)

            // Property 1: Sum exactly equals total
            assertEquals(totalPaise, shares.sum())
            // Property 2: No share is negative
            assertTrue(shares.all { it >= 0L })
            // Property 3: Difference between highest and lowest share is at most 1 paise
            val maxShare = shares.maxOrNull()!!
            val minShare = shares.minOrNull()!!
            assertTrue((maxShare - minShare) in 0L..1L)
        }
    }

    @Test
    fun testPropertyBased_SettlementReversalRestoresOriginalBalance() {
        val random = Random(123)
        for (i in 1..50) {
            val debtPaise = random.nextLong(100L, 500_000L)
            val txn = Transaction(
                title = "Random Debt",
                amountPaise = debtPaise * 2,
                paidByFriendId = null,
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = debtPaise),
                    TransactionParticipant(friendId = 101L, shareAmountPaise = debtPaise)
                )
            )
            // Initial balance: A owes Me debtPaise
            val initialLedger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), emptyList()).first()
            assertEquals(debtPaise, initialLedger.netBalancePaise)

            // Record settlement
            val settlement = Settlement(
                payerId = 101L,
                receiverId = null,
                amountPaise = debtPaise,
                isReversed = false
            )
            val settledLedger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(settlement)).first()
            assertEquals(0L, settledLedger.netBalancePaise)

            // Reverse settlement
            val reversedSettlement = settlement.copy(isReversed = true)
            val restoredLedger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(reversedSettlement)).first()
            // Property: Original balance perfectly restored
            assertEquals(debtPaise, restoredLedger.netBalancePaise)
        }
    }
}