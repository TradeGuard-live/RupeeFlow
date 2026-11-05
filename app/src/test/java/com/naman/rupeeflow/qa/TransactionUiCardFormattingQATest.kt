package com.naman.rupeeflow.qa

import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 5 — Transaction UI Display and Amount Formatting Tests
 * Tests the reversed split hierarchy:
 * Upper amount: "Your share: ₹X.XX" (bold, prominent)
 * Lower amount: "Total amount: ₹Y.YY" (secondary)
 * And ensures non-split expenses display the total amount directly without misleading share prefixes.
 */
class TransactionUiCardFormattingQATest {

    data class FormattedCardAmounts(
        val upperText: String,
        val isUpperProminent: Boolean,
        val lowerText: String?,
        val isLowerSecondary: Boolean
    )

    private fun extractCardDisplay(transaction: Transaction): FormattedCardAmounts {
        val isSplit = transaction.participants.size > 1
        val myParticipant = transaction.participants.firstOrNull { it.friendId == null }

        return if (isSplit && myParticipant != null && transaction.transactionType == TransactionType.EXPENSE) {
            val amountPrefix = if (transaction.transactionStatus == TransactionStatus.IGNORED) "" else "-"
            FormattedCardAmounts(
                upperText = "Your share: $amountPrefix${CurrencyUtils.formatPaiseToRupees(myParticipant.shareAmountPaise)}",
                isUpperProminent = true,
                lowerText = "Total amount: ${CurrencyUtils.formatPaiseToRupees(transaction.amountPaise)}",
                isLowerSecondary = true
            )
        } else {
            val amountPrefix = when (transaction.transactionType) {
                TransactionType.INCOME -> "+"
                TransactionType.EXPENSE -> if (transaction.transactionStatus == TransactionStatus.IGNORED) "" else "-"
                TransactionType.REFUND -> "+"
                TransactionType.TRANSFER -> ""
                else -> ""
            }
            FormattedCardAmounts(
                upperText = "$amountPrefix${CurrencyUtils.formatPaiseToRupees(transaction.amountPaise)}",
                isUpperProminent = true,
                lowerText = null,
                isLowerSecondary = false
            )
        }
    }

    @Test
    fun testSplitExpense_DisplaysYourShareAboveTotalAmount() {
        val splitTxn = Transaction(
            title = "Dinner with Friends",
            amountPaise = 4000L, // ₹40.00
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 2000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 2000L)
            )
        )
        val display = extractCardDisplay(splitTxn)

        // Upper side must be "Your share: -₹20.00"
        assertEquals("Your share: -₹20.00", display.upperText)
        assertTrue(display.isUpperProminent)

        // Lower side must be "Total amount: ₹40.00"
        assertNotNull(display.lowerText)
        assertEquals("Total amount: ₹40.00", display.lowerText)
        assertTrue(display.isLowerSecondary)
    }

    @Test
    fun testSoloExpense_DisplaysTotalAmountDirectlyWithoutMisleadingShare() {
        val soloTxn = Transaction(
            title = "Mobile Recharge",
            amountPaise = 29900L, // ₹299.00
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = emptyList() // No split
        )
        val display = extractCardDisplay(soloTxn)

        // Prominent amount is total amount directly
        assertEquals("-₹299.00", display.upperText)
        assertFalse(display.upperText.contains("Your share"))

        // No secondary split label
        assertNull(display.lowerText)
    }

    @Test
    fun testIncomeTransaction_DisplaysPositiveAmountWithoutSplitLabels() {
        val incomeTxn = Transaction(
            title = "Freelance Payment",
            amountPaise = 5000000L, // ₹50,000.00
            transactionType = TransactionType.INCOME,
            paidByFriendId = null
        )
        val display = extractCardDisplay(incomeTxn)
        assertEquals("+₹50,000.00", display.upperText)
        assertNull(display.lowerText)
    }

    @Test
    fun testRefundTransaction_DisplaysPositiveAmount() {
        val refundTxn = Transaction(
            title = "Amazon Return",
            amountPaise = 149900L, // ₹1,499.00
            transactionType = TransactionType.REFUND,
            paidByFriendId = null
        )
        val display = extractCardDisplay(refundTxn)
        assertEquals("+₹1,499.00", display.upperText)
        assertNull(display.lowerText)
    }

    @Test
    fun testTransferTransaction_DisplaysNeutralAmount() {
        val transferTxn = Transaction(
            title = "Self Transfer to Savings",
            amountPaise = 1000000L, // ₹10,000.00
            transactionType = TransactionType.TRANSFER,
            paidByFriendId = null
        )
        val display = extractCardDisplay(transferTxn)
        assertEquals("₹10,000.00", display.upperText)
        assertNull(display.lowerText)
    }

    @Test
    fun testSplitExpense_LargeAmountFormatting() {
        val largeTxn = Transaction(
            title = "Luxury Villa Booking",
            amountPaise = 120_000_000L, // ₹12,00,000.00
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30_000_000L), // ₹3,00,000.00
                TransactionParticipant(friendId = 101L, shareAmountPaise = 90_000_000L)
            )
        )
        val display = extractCardDisplay(largeTxn)
        assertEquals("Your share: -₹3,00,000.00", display.upperText)
        assertEquals("Total amount: ₹12,00,000.00", display.lowerText)
    }

    @Test
    fun testSplitExpense_OnePaiseValue() {
        val tinyTxn = Transaction(
            title = "Test Penny Split",
            amountPaise = 2L, // ₹0.02
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 1L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 1L)
            )
        )
        val display = extractCardDisplay(tinyTxn)
        assertEquals("Your share: -₹0.01", display.upperText)
        assertEquals("Total amount: ₹0.02", display.lowerText)
    }

    @Test
    fun testIgnoredSplitExpense_ShowsShareWithoutMinusPrefix() {
        val ignoredTxn = Transaction(
            title = "Duplicate Pizza Order",
            amountPaise = 60000L,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.IGNORED,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 30000L)
            )
        )
        val display = extractCardDisplay(ignoredTxn)
        assertEquals("Your share: ₹300.00", display.upperText)
        assertEquals("Total amount: ₹600.00", display.lowerText)
    }
}