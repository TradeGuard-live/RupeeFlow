package com.naman.rupeeflow.domain.accounting

import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalShareAccountingTest {

    // Test 1: Non-split personal expense counts full amount
    @Test
    fun testNonSplitPersonalExpenseCountsFullAmount() {
        val txn = Transaction(
            id = 1L,
            title = "Groceries",
            amountPaise = 150000L, // Rs 1,500
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = emptyList()
        )
        assertEquals(150000L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
        assertEquals(150000L, txn.userExpensePaise)
    }

    // Test 2: Equal split counts user's equal share
    @Test
    fun testEqualSplitCountsUserShare() {
        val txn = Transaction(
            id = 2L,
            title = "Dinner with Friend",
            amountPaise = 200000L, // Rs 2,000 total
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            splitMethod = SplitMethod.EQUAL,
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 100000L),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Rahul", shareAmountPaise = 100000L)
            )
        )
        assertEquals(100000L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
        assertEquals(100000L, txn.userExpensePaise)
    }

    // Test 3: Percentage split counts user's exact percentage
    @Test
    fun testPercentageSplitCountsExactUserAllocation() {
        val txn = Transaction(
            id = 3L,
            title = "Office Cab",
            amountPaise = 50000L, // Rs 500
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            splitMethod = SplitMethod.PERCENTAGE,
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 30000L, percentage = 60.0),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Amit", shareAmountPaise = 20000L, percentage = 40.0)
            )
        )
        assertEquals(30000L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
    }

    // Test 4: Custom split counts user's custom allocation
    @Test
    fun testCustomSplitCountsUserCustomAllocation() {
        val txn = Transaction(
            id = 4L,
            title = "Party Bill",
            amountPaise = 450000L, // Rs 4,500
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            splitMethod = SplitMethod.EXACT_AMOUNT,
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 150000L),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Priya", shareAmountPaise = 200000L),
                TransactionParticipant(id = 3L, friendId = 11L, friendName = "Rohan", shareAmountPaise = 100000L)
            )
        )
        assertEquals(150000L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
    }

    // Test 5: Expense paid by Me counts only my share, not friends' shares
    @Test
    fun testPaidByMeExcludesFriendsSharesFromPersonalExpense() {
        val txn = Transaction(
            id = 5L,
            title = "Movie Tickets",
            amountPaise = 120000L, // Rs 1,200 total paid by Me
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 40000L),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Friend A", shareAmountPaise = 40000L),
                TransactionParticipant(id = 3L, friendId = 11L, friendName = "Friend B", shareAmountPaise = 40000L)
            )
        )
        // User paid 120000, but only 40000 is user's personal expense; 80000 is receivable
        assertEquals(40000L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
    }

    // Test 6: Expense paid by a Friend counts user's share as personal expense
    @Test
    fun testPaidByFriendCountsUserShareAsPersonalExpense() {
        val txn = Transaction(
            id = 6L,
            title = "Weekend Resort",
            amountPaise = 1000000L, // Rs 10,000 paid by Friend
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = 10L,
            paidByName = "Karan",
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 250000L),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Karan", shareAmountPaise = 750000L)
            )
        )
        assertEquals(250000L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
    }

    // Test 7: Multiple friends split among 4+ participants
    @Test
    fun testMultipleFriendsSplitCalculatesMeCorrectly() {
        val txn = Transaction(
            id = 7L,
            title = "Farmhouse Rental",
            amountPaise = 800000L, // Rs 8,000
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = 20L,
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 200000L),
                TransactionParticipant(id = 2L, friendId = 20L, friendName = "Jay", shareAmountPaise = 200000L),
                TransactionParticipant(id = 3L, friendId = 21L, friendName = "Manan", shareAmountPaise = 200000L),
                TransactionParticipant(id = 4L, friendId = 22L, friendName = "Pranav", shareAmountPaise = 200000L)
            )
        )
        assertEquals(200000L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
    }

    // Test 8: Fractional paise / penny-exactness without float drift
    @Test
    fun testFractionalPaiseSplittingPreservesPennyExactness() {
        // Rs 100.00 (10000 paise) split among 3 people: 3334, 3333, 3333
        val txn = Transaction(
            id = 8L,
            title = "Three-way Split",
            amountPaise = 10000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 3334L),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Person 1", shareAmountPaise = 3333L),
                TransactionParticipant(id = 3L, friendId = 11L, friendName = "Person 2", shareAmountPaise = 3333L)
            )
        )
        assertEquals(3334L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
        assertEquals(10000L, txn.participants.sumOf { it.shareAmountPaise })
    }

    // Test 9: Edited allocation uses latest saved participant allocation
    @Test
    fun testEditedAllocationReflectedInPersonalExpense() {
        val initialTxn = Transaction(
            id = 9L,
            title = "Dinner",
            amountPaise = 100000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 50000L),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Friend", shareAmountPaise = 50000L)
            )
        )
        assertEquals(50000L, PersonalFinancialAggregator.calculatePersonalExpense(initialTxn))

        // Edit split so user's share is now 70000 paise
        val editedTxn = initialTxn.copy(
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 70000L),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Friend", shareAmountPaise = 30000L)
            )
        )
        assertEquals(70000L, PersonalFinancialAggregator.calculatePersonalExpense(editedTxn))
    }

    // Test 10: Participant list with no "Me" (user not part of split)
    @Test
    fun testSplitExcludingUserYieldsZeroPersonalExpense() {
        val txn = Transaction(
            id = 10L,
            title = "Gift for Colleague paid by Me on behalf of others",
            amountPaise = 200000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = 10L, friendName = "Friend A", shareAmountPaise = 100000L),
                TransactionParticipant(id = 2L, friendId = 11L, friendName = "Friend B", shareAmountPaise = 100000L)
            )
        )
        // User paid full amount on behalf of others, user's share is 0
        assertEquals(0L, PersonalFinancialAggregator.calculatePersonalExpense(txn))
    }

    // Test 11: Refund of split transaction
    @Test
    fun testRefundOfSplitTransaction() {
        val refundTxn = Transaction(
            id = 11L,
            title = "Concert Ticket Refund",
            amountPaise = 600000L,
            transactionType = TransactionType.REFUND,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 200000L),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Friend", shareAmountPaise = 400000L)
            )
        )
        assertEquals(200000L, PersonalFinancialAggregator.calculatePersonalRefund(refundTxn))
    }

    // Test 12: Partial refund accounting
    @Test
    fun testPartialRefundReducesNetSpending() {
        val expenseTxn = Transaction(
            id = 12L,
            title = "Electronics Purchase",
            amountPaise = 1000000L, // Rs 10,000
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null
        )
        val partialRefundTxn = Transaction(
            id = 13L,
            title = "Cashback Refund",
            amountPaise = 150000L, // Rs 1,500
            transactionType = TransactionType.REFUND,
            paidByFriendId = null
        )
        val net = PersonalFinancialAggregator.calculatePersonalNetSpending(listOf(expenseTxn, partialRefundTxn))
        assertEquals(850000L, net)
    }

    // Test 13: Full refund yields zero net personal spending
    @Test
    fun testFullRefundYieldsZeroNetSpending() {
        val expenseTxn = Transaction(
            id = 14L,
            title = "Cancelled Flight",
            amountPaise = 450000L,
            transactionType = TransactionType.EXPENSE
        )
        val refundTxn = Transaction(
            id = 15L,
            title = "Flight Refund",
            amountPaise = 450000L,
            transactionType = TransactionType.REFUND
        )
        val net = PersonalFinancialAggregator.calculatePersonalNetSpending(listOf(expenseTxn, refundTxn))
        assertEquals(0L, net)
    }

    // Test 14: Transfer exclusion from personal spending
    @Test
    fun testInternalTransferExcludedFromPersonalExpense() {
        val transferTxn = Transaction(
            id = 16L,
            title = "Account Self Transfer",
            amountPaise = 500000L,
            transactionType = TransactionType.TRANSFER
        )
        assertEquals(0L, PersonalFinancialAggregator.calculatePersonalExpense(transferTxn))
        assertEquals(0L, PersonalFinancialAggregator.calculatePersonalIncome(transferTxn))
    }

    // Test 15: Wallet top-up exclusion
    @Test
    fun testWalletTopUpExcludedFromPersonalExpense() {
        val topUpTxn = Transaction(
            id = 17L,
            title = "UPI Lite Top-up",
            amountPaise = 200000L,
            transactionType = TransactionType.UPI_LITE_TOPUP
        )
        assertEquals(0L, PersonalFinancialAggregator.calculatePersonalExpense(topUpTxn))
    }

    // Test 16: UPI Lite payment included in personal expense
    @Test
    fun testUpiLitePaymentIncludedInPersonalExpense() {
        val upiLiteTxn = Transaction(
            id = 18L,
            title = "Chai",
            amountPaise = 2000L, // Rs 20
            paymentMethod = PaymentMethod.UPI_LITE,
            transactionType = TransactionType.UPI_LITE_PAYMENT
        )
        assertEquals(2000L, PersonalFinancialAggregator.calculatePersonalExpense(upiLiteTxn))
        assertEquals(2000L, PersonalFinancialAggregator.calculatePersonalOnlineSpending(listOf(upiLiteTxn)))
    }

    // Test 17: Friend debt settlements excluded from personal expenses
    @Test
    fun testSettlementTransactionsExcludedFromExpenses() {
        val settlementTxn = Transaction(
            id = 19L,
            title = "Settlement from Rahul",
            amountPaise = 150000L,
            transactionType = TransactionType.DEBT_PAID
        )
        assertEquals(0L, PersonalFinancialAggregator.calculatePersonalExpense(settlementTxn))
    }

    // Test 18: Ignored transactions excluded
    @Test
    fun testIgnoredTransactionReturnsZeroPersonalExpense() {
        val ignoredTxn = Transaction(
            id = 20L,
            title = "Spam / Duplicate",
            amountPaise = 99900L,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.IGNORED
        )
        assertEquals(0L, PersonalFinancialAggregator.calculatePersonalExpense(ignoredTxn))
    }

    // Test 19: Deleted / soft-deleted transactions excluded from aggregation
    @Test
    fun testIgnoredStatusConsistentlyExcludedAcrossAllAggregations() {
        val activeTxn = Transaction(
            id = 21L,
            title = "Active Lunch",
            amountPaise = 40000L,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )
        val ignoredTxn = Transaction(
            id = 22L,
            title = "Ignored Lunch",
            amountPaise = 40000L,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.IGNORED
        )
        val list = listOf(activeTxn, ignoredTxn)
        val net = PersonalFinancialAggregator.calculatePersonalNetSpending(list)
        assertEquals(40000L, net)
    }

    // Test 20: Safe integer math / overflow handling for large paise values
    @Test
    fun testOverflowProtectionWithLargeValues() {
        val largeTxn1 = Transaction(
            id = 23L,
            title = "Major Investment",
            amountPaise = 500_000_000_00L, // Rs 50 Crore = 5,000,000,000 paise
            transactionType = TransactionType.EXPENSE
        )
        val largeTxn2 = Transaction(
            id = 24L,
            title = "Second Investment",
            amountPaise = 500_000_000_00L,
            transactionType = TransactionType.EXPENSE
        )
        val net = PersonalFinancialAggregator.calculatePersonalNetSpending(listOf(largeTxn1, largeTxn2))
        assertEquals(1_000_000_000_00L, net)
    }
}

