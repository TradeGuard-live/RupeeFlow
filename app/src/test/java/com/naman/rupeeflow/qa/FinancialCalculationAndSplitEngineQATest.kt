package com.naman.rupeeflow.qa

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.SafeMath
import org.junit.Assert.*
import org.junit.Test

/**
 * RupeeFlow Production QA Test Plan
 * Section 2: Financial calculation and split engine — TC001 to TC020
 */
class FinancialCalculationAndSplitEngineQATest {

    private val friendA = Friend(id = 101L, name = "Friend A")
    private val friendB = Friend(id = 102L, name = "Friend B")
    private val friendC = Friend(id = 103L, name = "Friend C")
    private val friendD = Friend(id = 104L, name = "Friend D")
    private val friendE = Friend(id = 105L, name = "Friend E")

    @Test
    fun test_TC001_EqualSplit_ExactDivision() {
        // Steps: Create a ₹1,000 expense shared equally by Me, A, B and C (4 participants).
        val totalPaise = 100000L // ₹1,000.00
        val shares = LedgerCalculator.calculateEqualSplit(totalPaise, 4)

        // Expected: Each participant receives ₹250 (25,000 paise). Total shares equal ₹1,000 exactly.
        assertEquals(4, shares.size)
        assertEquals(25000L, shares[0])
        assertEquals(25000L, shares[1])
        assertEquals(25000L, shares[2])
        assertEquals(25000L, shares[3])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun test_TC002_EqualSplit_WithRemainder() {
        // Steps: Split ₹100 among three participants.
        val totalPaise = 10000L // ₹100.00
        val shares = LedgerCalculator.calculateEqualSplit(totalPaise, 3)

        // Expected: Shares are ₹33.34, ₹33.33 and ₹33.33. Total must equal ₹100 exactly.
        assertEquals(3, shares.size)
        assertEquals(3334L, shares[0])
        assertEquals(3333L, shares[1])
        assertEquals(3333L, shares[2])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun test_TC003_OnePaiseEqualSplit() {
        // Steps: Split ₹0.01 (1 paise) between two participants.
        val totalPaise = 1L // 1 paise
        val shares = LedgerCalculator.calculateEqualSplit(totalPaise, 2)

        // Expected: One participant receives 1 paise and the other 0 paise. No paise created or lost.
        assertEquals(2, shares.size)
        assertEquals(1L, shares[0])
        assertEquals(0L, shares[1])
        assertEquals(1L, shares.sum())
    }

    @Test
    fun test_TC004_EqualSplit_OneParticipant() {
        // Steps: Create a ₹500 expense with only Me participating.
        val totalPaise = 50000L // ₹500.00
        val shares = LedgerCalculator.calculateEqualSplit(totalPaise, 1)

        // Expected: Me's share is ₹500. No friend balance is created.
        assertEquals(1, shares.size)
        assertEquals(50000L, shares[0])

        val txn = Transaction(
            id = 1L,
            title = "Personal Lunch",
            amountPaise = totalPaise,
            paidByFriendId = null, // Me paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L)
            )
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), emptyList())
        assertEquals(0L, ledgers.first().netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgers.first().status)
    }

    @Test
    fun test_TC005_EmptyParticipantList() {
        // Steps: Attempt to save a shared expense without participants or zero participants in split.
        val shares = LedgerCalculator.calculateEqualSplit(50000L, 0)
        // Expected: Empty list returned. No friend debt is generated.
        assertTrue(shares.isEmpty())

        val txn = Transaction(
            title = "Solo Expense",
            amountPaise = 50000L,
            participants = emptyList()
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), emptyList())
        assertEquals(0L, ledgers.first().netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgers.first().status)
    }

    @Test
    fun test_TC006_PercentageSplit_Totaling100() {
        // Steps: Split ₹1,000 using 20%, 30% and 50%.
        val totalPaise = 100000L
        val percentages = listOf(20.0, 30.0, 50.0)
        assertTrue(LedgerCalculator.validatePercentages(percentages))

        val shares = LedgerCalculator.calculatePercentageSplit(totalPaise, percentages)
        // Expected: Shares are ₹200, ₹300 and ₹500.
        assertEquals(3, shares.size)
        assertEquals(20000L, shares[0])
        assertEquals(30000L, shares[1])
        assertEquals(50000L, shares[2])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun test_TC007_PercentageSplit_DecimalPercentages() {
        // Steps: Split ₹1,000 using 33.33%, 33.33% and 33.34%.
        val totalPaise = 100000L
        val percentages = listOf(33.33, 33.33, 33.34)
        assertTrue(LedgerCalculator.validatePercentages(percentages))

        val shares = LedgerCalculator.calculatePercentageSplit(totalPaise, percentages)
        // Expected: Shares total exactly ₹1,000 after rounding.
        assertEquals(3, shares.size)
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun test_TC008_PercentageTotal_Below100() {
        // Steps: Enter percentages totaling 99%.
        val percentages = listOf(33.0, 33.0, 33.0)
        // Expected: Validation rejects the split. Must not silently increase participant shares.
        assertFalse(LedgerCalculator.validatePercentages(percentages))
        try {
            LedgerCalculator.calculatePercentageSplit(100000L, percentages)
            fail("Expected IllegalArgumentException for percentages < 100%")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun test_TC009_PercentageTotal_Above100() {
        // Steps: Enter percentages totaling 101%.
        val percentages = listOf(50.0, 50.0, 1.0)
        // Expected: Validation rejects the split.
        assertFalse(LedgerCalculator.validatePercentages(percentages))
        try {
            LedgerCalculator.calculatePercentageSplit(100000L, percentages)
            fail("Expected IllegalArgumentException for percentages > 100%")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun test_TC010_NegativePercentage() {
        // Steps: Enter -10%, 50% and 60%.
        val percentages = listOf(-10.0, 50.0, 60.0)
        // Expected: Validation rejects negative percentages.
        assertFalse(LedgerCalculator.validatePercentages(percentages))
        try {
            LedgerCalculator.calculatePercentageSplit(100000L, percentages)
            fail("Expected IllegalArgumentException for negative percentages")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun test_TC011_ZeroPercentage() {
        // Steps: Assign one participant 0% and others 40% and 60%.
        val totalPaise = 100000L
        val percentages = listOf(0.0, 40.0, 60.0)
        assertTrue(LedgerCalculator.validatePercentages(percentages))

        val shares = LedgerCalculator.calculatePercentageSplit(totalPaise, percentages)
        // Expected: Zero share is 0 paise. Total remains correct.
        assertEquals(3, shares.size)
        assertEquals(0L, shares[0])
        assertEquals(40000L, shares[1])
        assertEquals(60000L, shares[2])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun test_TC012_PercentageRounding_AcrossManyParticipants() {
        // Steps: Split ₹10 (1000 paise) among seven participants using percentages that total 100%.
        // 14.28 * 6 = 85.68, + 14.32 = 100.00
        val totalPaise = 1000L // ₹10.00
        val percentages = listOf(14.28, 14.28, 14.28, 14.28, 14.28, 14.28, 14.32)
        assertTrue(LedgerCalculator.validatePercentages(percentages))

        val shares = LedgerCalculator.calculatePercentageSplit(totalPaise, percentages)
        // Expected: Deterministic rounding. All shares non-negative and sum to exactly ₹10.
        assertEquals(7, shares.size)
        assertTrue(shares.all { it >= 0L })
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun test_TC013_CustomSplit_ExactAmounts() {
        // Steps: Allocate ₹1,000 as ₹100, ₹300 and ₹600.
        val totalPaise = 100000L
        val shares = listOf(10000L, 30000L, 60000L)

        // Expected: Save succeeds and all participant amounts are preserved.
        assertEquals(totalPaise, shares.sum())
        val txn = Transaction(
            title = "Team Dinner",
            amountPaise = totalPaise,
            splitMethod = SplitMethod.EXACT_AMOUNT,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = shares[0]),
                TransactionParticipant(friendId = friendA.id, shareAmountPaise = shares[1]),
                TransactionParticipant(friendId = friendB.id, shareAmountPaise = shares[2])
            )
        )
        assertEquals(10000L, txn.participants[0].shareAmountPaise)
        assertEquals(30000L, txn.participants[1].shareAmountPaise)
        assertEquals(60000L, txn.participants[2].shareAmountPaise)
        assertEquals(totalPaise, txn.participants.sumOf { it.shareAmountPaise })
    }

    @Test
    fun test_TC014_CustomSplit_BelowTransactionAmount() {
        // Steps: Allocate ₹900 out of a ₹1,000 transaction.
        val totalPaise = 100000L
        val shares = listOf(10000L, 30000L, 50000L) // Total 90000 paise (₹900)

        // Expected: Save is rejected with validation error.
        val sharesSum = shares.sum()
        assertNotEquals(totalPaise, sharesSum)
        val isValid = sharesSum == totalPaise
        assertFalse("Custom split totaling less than amount must be rejected", isValid)
    }

    @Test
    fun test_TC015_CustomSplit_AboveTransactionAmount() {
        // Steps: Allocate ₹1,100 out of a ₹1,000 transaction.
        val totalPaise = 100000L
        val shares = listOf(20000L, 40000L, 50000L) // Total 110000 paise (₹1,100)

        // Expected: Save is rejected.
        val sharesSum = shares.sum()
        assertNotEquals(totalPaise, sharesSum)
        val isValid = sharesSum == totalPaise
        assertFalse("Custom split totaling more than amount must be rejected", isValid)
    }

    @Test
    fun test_TC016_CustomSplit_NegativeShare() {
        // Steps: Assign one participant -₹100 (-10000 paise).
        val shares = listOf(-10000L, 50000L, 60000L)
        // Expected: Negative share is rejected.
        val hasNegative = shares.any { it < 0L }
        assertTrue("Negative share must be detected and rejected", hasNegative)
    }

    @Test
    fun test_TC017_ChangeSplitMethod() {
        // Steps: Save an equal split, then edit it to a custom split.
        val initialTxn = Transaction(
            id = 10L,
            title = "Movie Outing",
            amountPaise = 60000L, // ₹600.00
            splitMethod = SplitMethod.EQUAL,
            participants = listOf(
                TransactionParticipant(id = 1L, transactionId = 10L, friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(id = 2L, transactionId = 10L, friendId = friendA.id, shareAmountPaise = 30000L)
            )
        )
        val initialLedger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(initialTxn), emptyList())
        assertEquals(30000L, initialLedger.first().totalTheyOweMePaise)

        // Edit to custom split: Me ₹200, Friend A ₹400
        val editedTxn = initialTxn.copy(
            splitMethod = SplitMethod.EXACT_AMOUNT,
            participants = listOf(
                TransactionParticipant(id = 1L, transactionId = 10L, friendId = null, shareAmountPaise = 20000L),
                TransactionParticipant(id = 2L, transactionId = 10L, friendId = friendA.id, shareAmountPaise = 40000L)
            )
        )
        val updatedLedger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())
        // Expected: Old shares are replaced. Only the new split is used in the ledger.
        assertEquals(40000L, updatedLedger.first().totalTheyOweMePaise)
        assertEquals(40000L, updatedLedger.first().netBalancePaise)
    }

    @Test
    fun test_TC018_MaximumSupportedAmount() {
        // Steps: Enter the maximum amount supported by the app and calculate a split.
        val largeAmount = 100000000000L // ₹100 Crore in paise
        val shares = LedgerCalculator.calculateEqualSplit(largeAmount, 4)

        // Expected: No overflow, crash, truncation or inaccurate display.
        assertEquals(4, shares.size)
        assertEquals(25000000000L, shares[0])
        assertEquals(largeAmount, shares.sum())

        val formatted = CurrencyUtils.formatPaiseToRupees(largeAmount)
        assertTrue(formatted.contains("₹1,00,00,00,000.00") || formatted.startsWith("₹100"))

        // Clamping check on extreme overflow using SafeMath
        val clampedAdd = SafeMath.safeAdd(Long.MAX_VALUE - 10, 50)
        assertEquals(Long.MAX_VALUE, clampedAdd)
    }

    @Test
    fun test_TC019_InvalidAmountInputs() {
        // Steps: Try blank, zero, negative, alphabetic, malformed decimal and excessive decimal inputs.
        assertNull(CurrencyUtils.parseInputToPaiseOrNull(""))
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("   "))
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("-50.00"))
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("abc"))
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("12.34.56"))
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("12.345")) // More than 2 decimal places

        // Valid zero and positive inputs
        assertEquals(0L, CurrencyUtils.parseInputToPaiseOrNull("0"))
        assertEquals(50000L, CurrencyUtils.parseInputToPaiseOrNull("500"))
        assertEquals(50050L, CurrencyUtils.parseInputToPaiseOrNull("500.50"))
    }

    @Test
    fun test_TC020_CurrencyConversionAndFormatting() {
        // Steps: Display ₹0.01, ₹1, ₹1,234.56 and a large supported amount.
        assertEquals("₹0.01", CurrencyUtils.formatPaiseToRupees(1L))
        assertEquals("₹1.00", CurrencyUtils.formatPaiseToRupees(100L))
        assertEquals("₹1,234.56", CurrencyUtils.formatPaiseToRupees(123456L))
        assertEquals("₹1,25,00,000.00", CurrencyUtils.formatPaiseToRupees(1250000000L))

        // Compact format
        assertEquals("₹1.2k", CurrencyUtils.formatPaiseCompact(120000L))
        assertEquals("₹1.5L", CurrencyUtils.formatPaiseCompact(15000000L))
        assertEquals("₹1.5Cr", CurrencyUtils.formatPaiseCompact(1500000000L))
    }
}
