package com.naman.rupeeflow.domain.accounting

import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

class AnalyticsAndReportConsistencyTest {

    private val sampleTransactions = listOf(
        // Personal non-split expense
        Transaction(
            id = 1L,
            title = "Personal Lunch",
            amountPaise = 45000L, // Rs 450
            categoryId = 1L,
            transactionDate = 1794528000000L,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            expenseGroupId = 100L
        ),
        // Split expense paid by Me (Total Rs 1,605, Me = Rs 200.63 -> 20063 paise, Friend = Rs 1404.37)
        Transaction(
            id = 2L,
            title = "Team Dinner at Restaurant",
            amountPaise = 160500L, // Rs 1,605
            categoryId = 1L,
            transactionDate = 1794528000000L,
            paymentMethod = PaymentMethod.CREDIT_CARD,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            expenseGroupId = 100L,
            expenseGroupName = "Farm & Caves",
            participants = listOf(
                TransactionParticipant(id = 1L, friendId = null, friendName = "Me", shareAmountPaise = 20063L),
                TransactionParticipant(id = 2L, friendId = 10L, friendName = "Rahul", shareAmountPaise = 140437L)
            )
        ),
        // Split expense paid by Friend (Total Rs 5,000, Me = Rs 2,500)
        Transaction(
            id = 3L,
            title = "Resort Stay",
            amountPaise = 500000L,
            categoryId = 2L,
            transactionDate = 1794528000000L,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = 10L,
            paidByName = "Rahul",
            expenseGroupId = 100L,
            expenseGroupName = "Farm & Caves",
            participants = listOf(
                TransactionParticipant(id = 3L, friendId = null, friendName = "Me", shareAmountPaise = 250000L),
                TransactionParticipant(id = 4L, friendId = 10L, friendName = "Rahul", shareAmountPaise = 250000L)
            )
        ),
        // Cash expense
        Transaction(
            id = 4L,
            title = "Auto Rickshaw",
            amountPaise = 15000L, // Rs 150
            categoryId = 3L,
            transactionDate = 1794528000000L,
            paymentMethod = PaymentMethod.CASH,
            transactionType = TransactionType.EXPENSE
        ),
        // Salary income
        Transaction(
            id = 5L,
            title = "Consulting Income",
            amountPaise = 800000L, // Rs 8,000
            categoryId = 4L,
            transactionDate = 1794528000000L,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            transactionType = TransactionType.INCOME
        ),
        // Refund
        Transaction(
            id = 6L,
            title = "Refund for cancelled order",
            amountPaise = 5000L, // Rs 50
            categoryId = 1L,
            transactionDate = 1794528000000L,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.REFUND
        ),
        // Internal transfer (excluded)
        Transaction(
            id = 7L,
            title = "Self Transfer",
            amountPaise = 100000L,
            transactionDate = 1794528000000L,
            transactionType = TransactionType.TRANSFER
        ),
        // Ignored transaction (excluded)
        Transaction(
            id = 8L,
            title = "Spam / Duplicate",
            amountPaise = 99999L,
            transactionDate = 1794528000000L,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.IGNORED
        )
    )

    // Test 21: Analytics and report match on shared dataset
    @Test
    fun testAnalyticsAndReportMetricsMatchExact() {
        // Expected personal expenses:
        // Txn 1: 45000
        // Txn 2: 20063 (personal share of 160500)
        // Txn 3: 250000 (personal share of 500000)
        // Txn 4: 15000
        // Total = 45000 + 20063 + 250000 + 15000 = 330063 paise (Rs 3,300.63)
        // Gross total = 45000 + 160500 + 500000 + 15000 = 720500 paise (Rs 7,205.00)

        val totalPersonalExpense = sampleTransactions.sumOf { PersonalFinancialAggregator.calculatePersonalExpense(it) }
        val totalGrossBill = PersonalFinancialAggregator.calculateGrossBillAmount(sampleTransactions)
        val totalIncome = sampleTransactions.sumOf { PersonalFinancialAggregator.calculatePersonalIncome(it) }
        val totalRefund = sampleTransactions.sumOf { PersonalFinancialAggregator.calculatePersonalRefund(it) }
        val netSpending = PersonalFinancialAggregator.calculatePersonalNetSpending(sampleTransactions)

        assertEquals(330063L, totalPersonalExpense)
        assertEquals(720500L, totalGrossBill)
        assertEquals(800000L, totalIncome)
        assertEquals(5000L, totalRefund)
        assertEquals(325063L, netSpending) // 330063 - 5000
    }

    // Test 22: Weekly match
    @Test
    fun testWeeklyAggregatedPersonalExpensesMatch() {
        val weeklyTotals = PersonalFinancialAggregator.calculatePersonalDailyTotals(
            transactions = sampleTransactions,
            currentStartMillis = 1794528000000L,
            zoneId = ZoneId.of("UTC")
        )
        val sumOfDays = weeklyTotals.sumOf { it.amountPaise }
        val totalPersonalExpense = sampleTransactions.sumOf { PersonalFinancialAggregator.calculatePersonalExpense(it) }
        assertEquals(totalPersonalExpense, sumOfDays)
    }

    // Test 23: Monthly match
    @Test
    fun testMonthlyAggregatedPersonalExpensesMatch() {
        val yearMonth = YearMonth.of(2026, 11)
        val weeklyTotals = PersonalFinancialAggregator.calculatePersonalWeeklyTotals(
            transactions = sampleTransactions,
            yearMonth = yearMonth,
            timeZone = TimeZone.getTimeZone("UTC"),
            locale = Locale.US
        )
        val sumOfWeeks = weeklyTotals.sumOf { it.amountPaise }
        val totalPersonalExpense = sampleTransactions.sumOf { PersonalFinancialAggregator.calculatePersonalExpense(it) }
        assertEquals(totalPersonalExpense, sumOfWeeks)
    }

    // Test 24: Yearly match
    @Test
    fun testYearlyAggregatedPersonalExpensesMatch() {
        val monthlyTotals = PersonalFinancialAggregator.calculatePersonalMonthlyTotals(
            transactions = sampleTransactions,
            year = 2026,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.US
        )
        val sumOfMonths = monthlyTotals.sumOf { it.amountPaise }
        val totalPersonalExpense = sampleTransactions.sumOf { PersonalFinancialAggregator.calculatePersonalExpense(it) }
        assertEquals(totalPersonalExpense, sumOfMonths)
    }

    // Test 25: Custom date range match
    @Test
    fun testCustomDateRangeAggregation() {
        val start = 1794527000000L
        val end = 1794529000000L
        val inRange = sampleTransactions.filter { it.transactionDate in start..end }
        val personalExpenses = inRange.sumOf { PersonalFinancialAggregator.calculatePersonalExpense(it) }
        assertEquals(330063L, personalExpenses)
    }

    // Test 26: Category totals use personal shares, not gross bill amounts
    @Test
    fun testCategoryTotalsSumPersonalSharesNotGrossAmounts() {
        val categoryTotals = PersonalFinancialAggregator.calculatePersonalCategoryTotals(sampleTransactions)
        // Category 1: Txn 1 (45000) + Txn 2 personal share (20063) = 65063 paise
        assertEquals(65063L, categoryTotals[1L])
        // Category 2: Txn 3 personal share (250000)
        assertEquals(250000L, categoryTotals[2L])
        // Category 3: Txn 4 (15000)
        assertEquals(15000L, categoryTotals[3L])
    }

    // Test 27: Cash totals match across views
    @Test
    fun testCashSpendingMatchesAcrossViews() {
        val cash = PersonalFinancialAggregator.calculatePersonalCashSpending(sampleTransactions)
        assertEquals(15000L, cash) // Only Txn 4 is cash
    }

    // Test 28: Online totals match across views
    @Test
    fun testOnlineSpendingMatchesAcrossViews() {
        val online = PersonalFinancialAggregator.calculatePersonalOnlineSpending(sampleTransactions)
        // Online: Txn 1 (45000) + Txn 2 (20063) + Txn 3 (250000) = 315063 paise
        assertEquals(315063L, online)
        val cash = PersonalFinancialAggregator.calculatePersonalCashSpending(sampleTransactions)
        val totalExpenses = sampleTransactions.sumOf { PersonalFinancialAggregator.calculatePersonalExpense(it) }
        assertEquals(totalExpenses, cash + online)
    }

    // Test 29: Expense group totals sum personal shares
    @Test
    fun testExpenseGroupTotalsSumPersonalShares() {
        val groupTotals = PersonalFinancialAggregator.calculatePersonalExpenseGroupTotals(sampleTransactions)
        // Group 100L: Txn 1 (45000) + Txn 2 (20063) + Txn 3 (250000) = 315063 paise
        assertEquals(315063L, groupTotals[100L])
    }

    // Test 30: Daily average denominator consistency
    @Test
    fun testDailyAverageCalculationConsistency() {
        val totalSpent = 330063L
        val daysInPeriod = 30
        val dailyAvg = totalSpent / daysInPeriod
        assertEquals(11002L, dailyAvg) // 330063 / 30 = 11002 paise
    }

    // Test 31: Report-specific exclusions filter without mutating underlying records
    @Test
    fun testReportSpecificExclusionReducesReportWithoutMutatingTransactions() {
        val excludedIds = setOf(1L) // Exclude Txn 1 (45000 paise)
        val filteredTxns = sampleTransactions.filter { it.id !in excludedIds }
        val filteredPersonalExpense = filteredTxns.sumOf { PersonalFinancialAggregator.calculatePersonalExpense(it) }

        assertEquals(285063L, filteredPersonalExpense) // 330063 - 45000
        // Original sampleTransactions remains unmutated with 8 items
        assertEquals(8, sampleTransactions.size)
    }

    // Test 32: Exclusion does not modify ledger
    @Test
    fun testExclusionDoesNotModifyUnderlyingTransactionStatusOrAmount() {
        val txn = sampleTransactions.first { it.id == 1L }
        assertEquals(TransactionStatus.ACTIVE, txn.transactionStatus)
        assertEquals(45000L, txn.amountPaise)
    }

    // Test 33: Empty report handled cleanly
    @Test
    fun testEmptyTransactionSetProducesZeroMetrics() {
        val emptyList = emptyList<Transaction>()
        assertEquals(0L, PersonalFinancialAggregator.calculatePersonalNetSpending(emptyList))
        assertEquals(0L, PersonalFinancialAggregator.calculatePersonalCashSpending(emptyList))
        assertEquals(0L, PersonalFinancialAggregator.calculatePersonalOnlineSpending(emptyList))
        assertEquals(0L, PersonalFinancialAggregator.calculateGrossBillAmount(emptyList))
        assertTrue(PersonalFinancialAggregator.calculatePersonalCategoryTotals(emptyList).isEmpty())
        assertTrue(PersonalFinancialAggregator.calculatePersonalExpenseGroupTotals(emptyList).isEmpty())
        assertTrue(PersonalFinancialAggregator.generateReconciliationDiagnostics(emptyList).isEmpty())
    }

    // Test 34: Large transaction history aggregated deterministically and performantly
    @Test
    fun testLargeTransactionHistoryPerformanceAndDeterminism() {
        val largeList = (1..10000).map { i ->
            Transaction(
                id = i.toLong(),
                title = "Item $i",
                amountPaise = 10000L,
                transactionType = TransactionType.EXPENSE,
                paymentMethod = if (i % 2 == 0) PaymentMethod.CASH else PaymentMethod.UPI
            )
        }
        val startTime = System.currentTimeMillis()
        val total = largeList.sumOf { PersonalFinancialAggregator.calculatePersonalExpense(it) }
        val cash = PersonalFinancialAggregator.calculatePersonalCashSpending(largeList)
        val online = PersonalFinancialAggregator.calculatePersonalOnlineSpending(largeList)
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(100_000_000L, total) // 10,000 * 10,000 paise = 100,000,000 paise
        assertEquals(50_000_000L, cash)
        assertEquals(50_000_000L, online)
        assertTrue("Aggregating 10,000 transactions must complete in under 2000ms", elapsed < 2000L)
    }

    // Test 35: Diagnostic reconciliation output flags discrepancies
    @Test
    fun testDiagnosticReconciliationIdentifiesGrossVsPersonalDifferences() {
        val diagnostics = PersonalFinancialAggregator.generateReconciliationDiagnostics(sampleTransactions)
        assertEquals(sampleTransactions.size, diagnostics.size)

        // Find restaurant split entry
        val restaurantEntry = diagnostics.first { it.transactionId == 2L }
        assertEquals(160500L, restaurantEntry.grossAmountPaise)
        assertEquals(20063L, restaurantEntry.personalSharePaise)
        assertTrue(restaurantEntry.isIncluded)
        assertEquals("SPLIT_EXPENSE_USER_SHARE", restaurantEntry.inclusionReason)

        // Find transfer entry
        val transferEntry = diagnostics.first { it.transactionId == 7L }
        assertFalse(transferEntry.isIncluded)
        assertEquals(0L, transferEntry.personalSharePaise)
    }
}

