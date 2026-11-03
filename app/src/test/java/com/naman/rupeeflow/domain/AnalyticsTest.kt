package com.naman.rupeeflow.domain

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {

    @Test
    fun testFinancialSummaryCalculation() {
        val now = System.currentTimeMillis()
        val startOfMonth = DateTimeUtils.getStartOfMonth(now)

        val txns = listOf(
            Transaction(
                id = 1L,
                title = "Swiggy",
                amountPaise = 50000L, // ₹500
                categoryId = 1L,
                transactionDate = now,
                paymentMethod = PaymentMethod.UPI,
                transactionType = TransactionType.EXPENSE
            ),
            Transaction(
                id = 2L,
                title = "Grocery Cash",
                amountPaise = 30000L, // ₹300
                categoryId = 2L,
                transactionDate = now,
                paymentMethod = PaymentMethod.CASH,
                transactionType = TransactionType.EXPENSE
            ),
            Transaction(
                id = 3L,
                title = "Freelance Project",
                amountPaise = 200000L, // ₹2000
                categoryId = 3L,
                transactionDate = now,
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                transactionType = TransactionType.INCOME
            ),
            Transaction(
                id = 4L,
                title = "Self Transfer",
                amountPaise = 500000L,
                categoryId = 1L,
                transactionDate = now,
                paymentMethod = PaymentMethod.UPI,
                transactionType = TransactionType.TRANSFER // TRANSFER excluded from spending
            ),
            Transaction(
                id = 5L,
                title = "Old Ignored Expense",
                amountPaise = 100000L,
                categoryId = 1L,
                transactionDate = now,
                paymentMethod = PaymentMethod.UPI,
                transactionType = TransactionType.EXPENSE,
                transactionStatus = TransactionStatus.IGNORED // IGNORED excluded
            )
        )

        val friends = listOf(
            Friend(id = 1L, name = "Ronit", netBalancePaise = 40000L), // Owed to me: ₹400
            Friend(id = 2L, name = "Manali", netBalancePaise = -15000L) // I owe: ₹150
        )

        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = txns,
            friends = friends,
            pendingCount = 2,
            startOfMonth = startOfMonth,
            now = now
        )

        // Expense: 500 + 300 = 800 (80000 paise)
        assertEquals(80000L, summary.totalExpenseThisMonthPaise)

        // Income: 2000 (200000 paise)
        assertEquals(200000L, summary.totalIncomeThisMonthPaise)

        // Cash: 300 (30000 paise)
        assertEquals(30000L, summary.totalCashSpendingPaise)

        // Online: 500 (50000 paise)
        assertEquals(50000L, summary.totalOnlineSpendingPaise)

        // Friends receivables & payables
        assertEquals(40000L, summary.totalOwedToMePaise)
        assertEquals(15000L, summary.totalIOwePaise)
        assertEquals(25000L, summary.netReceivablesPayablesPaise)

        // Pending count
        assertEquals(2, summary.pendingTransactionsCount)
    }
}
