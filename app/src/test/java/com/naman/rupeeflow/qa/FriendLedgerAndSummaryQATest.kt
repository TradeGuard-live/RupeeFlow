package com.naman.rupeeflow.qa

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DateTimeUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * RupeeFlow Production QA Test Plan
 * Section 4: Friend ledger and financial summaries — TC036 to TC048
 */
class FriendLedgerAndSummaryQATest {

    private val friendA = Friend(id = 101L, name = "Friend A")
    private val friendB = Friend(id = 102L, name = "Friend B")
    private val friendC = Friend(id = 103L, name = "Friend C")

    @Test
    fun test_TC036_MePaysForAFriend() {
        // Steps: Me pays ₹1,000. Friend A's share is ₹400.
        val txn = Transaction(
            title = "Dinner",
            amountPaise = 100000L,
            paidByFriendId = null, // Me paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 60000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 40000L)
            )
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }

        // Expected: A owes Me ₹400.
        assertEquals(40000L, ledgerA.totalTheyOweMePaise)
        assertEquals(0L, ledgerA.totalIOweThemPaise)
        assertEquals(40000L, ledgerA.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ledgerA.status)
    }

    @Test
    fun test_TC037_FriendPaysForMe() {
        // Steps: Friend A pays ₹1,000. Me's share is ₹400.
        val txn = Transaction(
            title = "Dinner",
            amountPaise = 100000L,
            paidByFriendId = 101L, // Friend A paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 60000L)
            )
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }

        // Expected: Me owes A ₹400.
        assertEquals(40000L, ledgerA.totalIOweThemPaise)
        assertEquals(0L, ledgerA.totalTheyOweMePaise)
        assertEquals(-40000L, ledgerA.netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledgerA.status)
    }

    @Test
    fun test_TC038_ExpenseSharedByMultipleFriends() {
        // Steps: Me pays ₹1,200. A owes ₹300, B owes ₹400 and C owes ₹500.
        val txn = Transaction(
            title = "Party Outing",
            amountPaise = 120000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = 101L, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 103L, shareAmountPaise = 50000L)
            )
        )
        val friends = listOf(friendA, friendB, friendC)
        val ledgers = LedgerCalculator.computeFriendLedgers(friends, listOf(txn), emptyList())

        // Expected: Total receivable is ₹1,200.
        val totalReceivable = ledgers.sumOf { it.totalTheyOweMePaise }
        assertEquals(120000L, totalReceivable)
        assertEquals(30000L, ledgers.first { it.id == 101L }.totalTheyOweMePaise)
        assertEquals(40000L, ledgers.first { it.id == 102L }.totalTheyOweMePaise)
        assertEquals(50000L, ledgers.first { it.id == 103L }.totalTheyOweMePaise)
    }

    @Test
    fun test_TC039_MultipleExpensesWithSameFriend() {
        // Steps: Create three expenses involving A: ₹100, ₹200 and ₹300 owed to Me.
        val t1 = Transaction(title = "Expense 1", amountPaise = 10000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 10000L)))
        val t2 = Transaction(title = "Expense 2", amountPaise = 20000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 20000L)))
        val t3 = Transaction(title = "Expense 3", amountPaise = 30000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 30000L)))

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(t1, t2, t3), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }

        // Expected: A's total receivable is ₹600.
        assertEquals(60000L, ledgerA.totalTheyOweMePaise)
        assertEquals(60000L, ledgerA.netBalancePaise)
    }

    @Test
    fun test_TC040_BothDirectionsWithSameFriend() {
        // Steps: A owes Me ₹700 from one transaction, while Me owes A ₹200 from another.
        val t1 = Transaction(title = "Dinner", amountPaise = 70000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 70000L)))
        val t2 = Transaction(title = "Movie", amountPaise = 20000L, paidByFriendId = 101L, participants = listOf(TransactionParticipant(friendId = null, shareAmountPaise = 20000L)))

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(t1, t2), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }

        // Expected: Net balance is ₹500 owed by A to Me.
        assertEquals(50000L, ledgerA.totalTheyOweMePaise)
        assertEquals(0L, ledgerA.totalIOweThemPaise)
        assertEquals(50000L, ledgerA.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ledgerA.status)
    }

    @Test
    fun test_TC041_NetBalanceReachesZero() {
        // Steps: Create equal opposite balances for Friend A (A owes Me ₹500, Me owes A ₹500).
        val t1 = Transaction(title = "Lunch", amountPaise = 50000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)))
        val t2 = Transaction(title = "Dinner", amountPaise = 50000L, paidByFriendId = 101L, participants = listOf(TransactionParticipant(friendId = null, shareAmountPaise = 50000L)))

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(t1, t2), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }

        // Expected: A's status is SETTLED and net balance is zero.
        assertEquals(0L, ledgerA.netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgerA.status)
    }

    @Test
    fun test_TC042_SettlementExceedsCurrentDebt() {
        // Steps: A owes Me ₹500 and makes a valid ₹700 settlement.
        val txn = Transaction(title = "Lunch", amountPaise = 50000L, paidByFriendId = null, participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)))
        val settlement = Settlement(payerId = 101L, receiverId = null, amountPaise = 70000L) // A paid Me ₹700

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(txn), listOf(settlement))
        val ledgerA = ledgers.first { it.id == 101L }

        // Expected: Resulting ₹200 reverse balance (Me owes A ₹200).
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledgerA.status)
        assertEquals(20000L, ledgerA.totalIOweThemPaise)
        assertEquals(0L, ledgerA.totalTheyOweMePaise)
        assertEquals(-20000L, ledgerA.netBalancePaise)
    }

    @Test
    fun test_TC043_IgnoredExpenseExclusion() {
        // Steps: Create a shared expense, then ignore it.
        val ignoredTxn = Transaction(
            title = "Ignored Shared",
            amountPaise = 50000L,
            paidByFriendId = null,
            participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)),
            transactionStatus = TransactionStatus.IGNORED
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(ignoredTxn), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }

        // Expected: It no longer affects active friend balances or active expense totals.
        assertEquals(0L, ledgerA.totalTheyOweMePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgerA.status)
    }

    @Test
    fun test_TC044_RestoreIgnoredExpense() {
        // Steps: Restore the transaction.
        val restoredTxn = Transaction(
            title = "Restored Shared",
            amountPaise = 50000L,
            paidByFriendId = null,
            participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)),
            transactionStatus = TransactionStatus.ACTIVE
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(restoredTxn), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }

        // Expected: Its original financial effect returns exactly once.
        assertEquals(50000L, ledgerA.totalTheyOweMePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ledgerA.status)
    }

    @Test
    fun test_TC045_IncomeExclusionFromFriendLedger() {
        // Steps: Record ₹5,000 ordinary income.
        val income = Transaction(
            title = "Salary",
            amountPaise = 500000L,
            transactionType = TransactionType.INCOME
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(income), emptyList())

        // Expected: Friend receivables and payables do not change.
        assertEquals(0L, ledgers.first().totalTheyOweMePaise)
        assertEquals(0L, ledgers.first().totalIOweThemPaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgers.first().status)
    }

    @Test
    fun test_TC046_TransferExclusionFromFriendLedger() {
        // Steps: Record a transfer.
        val transfer = Transaction(
            title = "Self Transfer",
            amountPaise = 200000L,
            transactionType = TransactionType.TRANSFER
        )
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(transfer), emptyList())

        // Expected: Shared expense ledger remains unchanged.
        assertEquals(0L, ledgers.first().totalTheyOweMePaise)
        assertEquals(0L, ledgers.first().totalIOweThemPaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgers.first().status)
    }

    @Test
    fun test_TC047_MonthlySummaryCalculation() {
        val now = System.currentTimeMillis()
        val startOfMonth = DateTimeUtils.getStartOfMonth(now)

        // Steps: Add expenses, income and refunds in the current month.
        val txns = listOf(
            Transaction(title = "Shopping", amountPaise = 150000L, transactionDate = now, transactionType = TransactionType.EXPENSE), // ₹1,500
            Transaction(title = "Salary", amountPaise = 500000L, transactionDate = now, transactionType = TransactionType.INCOME), // ₹5,000
            Transaction(title = "Item Return", amountPaise = 30000L, transactionDate = now, transactionType = TransactionType.REFUND) // ₹300
        )

        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = txns,
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = startOfMonth,
            now = now
        )

        // Expected: Gross expense, income, refund and net spending values are independently correct.
        assertEquals(150000L, summary.totalExpenseThisMonthPaise)
        assertEquals(500000L, summary.totalIncomeThisMonthPaise)
        assertEquals(30000L, summary.totalRefundThisMonthPaise)
        assertEquals(120000L, summary.netSpendingPaise) // 1,500 - 300 = ₹1,200
        val netSavings = summary.totalIncomeThisMonthPaise - summary.netSpendingPaise
        assertEquals(380000L, netSavings) // 5,000 - 1,200 = ₹3,800
    }

    @Test
    fun test_TC048_FutureDatedTransaction() {
        val now = System.currentTimeMillis()
        val startOfMonth = DateTimeUtils.getStartOfMonth(now)
        val futureDate = now + 86400000L * 10 // 10 days in the future

        // Steps: Add an expense dated in the future.
        val futureTxn = Transaction(
            title = "Upcoming Subscription",
            amountPaise = 99900L,
            transactionDate = futureDate,
            transactionType = TransactionType.EXPENSE
        )

        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = listOf(futureTxn),
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = startOfMonth,
            now = now
        )

        // Expected: It is excluded from current-period summary calculations until its date is reached.
        assertEquals(0L, summary.totalExpenseThisMonthPaise)
        assertEquals(0L, summary.netSpendingPaise)
    }
}
