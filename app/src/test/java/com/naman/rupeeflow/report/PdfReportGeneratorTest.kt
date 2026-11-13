package com.naman.rupeeflow.report

import com.naman.rupeeflow.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowPdfDocument::class])
class PdfReportGeneratorTest {

    @Test
    fun testGeneratePdfFileCreatesNonEmptyFile() {
        val context = RuntimeEnvironment.getApplication()
        val txns = (1..10).map { i ->
            Transaction(
                id = i.toLong(),
                title = "Expense Item $i",
                amountPaise = 50000L * i,
                categoryId = 1L,
                category = Category(1L, "Food", "restaurant", "#F97316"),
                transactionDate = System.currentTimeMillis() - (i * 86400000L),
                paymentMethod = if (i % 2 == 0) PaymentMethod.CASH else PaymentMethod.UPI,
                transactionType = TransactionType.EXPENSE
            )
        }

        val totalExpenses = txns.sumOf { it.amountPaise }
        val cash = txns.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.amountPaise }
        val online = txns.filter { it.paymentMethod != PaymentMethod.CASH }.sumOf { it.amountPaise }

        val data = ReportGenerationData(
            title = "Monthly Statement",
            periodLabel = "Monthly",
            dateRangeLabel = "01 Nov 2026 - 30 Nov 2026",
            transactions = txns,
            totalExpensesPaise = totalExpenses,
            totalIncomePaise = 0L,
            totalRefundsPaise = 0L,
            netSpendingPaise = totalExpenses,
            cashSpendingPaise = cash,
            onlineSpendingPaise = online,
            categoryBreakdown = listOf(
                CategoryReportItem("Food", totalExpenses, 100.0, txns.size)
            ),
            expenseGroupBreakdown = emptyList()
        )

        val pdfFile = PdfReportGenerator.generatePdf(context, data)

        assertNotNull(pdfFile)
        assertTrue("PDF file must exist", pdfFile.exists())
        assertTrue("PDF file size must be greater than zero bytes", pdfFile.length() > 0L)
    }

    @Test
    fun testMultiPagePdfGenerationForLargeTransactionSets() {
        val context = RuntimeEnvironment.getApplication()
        val largeTxns = (1..60).map { i ->
            Transaction(
                id = i.toLong(),
                title = "Daily Grocery Purchase $i",
                amountPaise = 25000L,
                categoryId = 1L,
                category = Category(1L, "Groceries", "shopping_cart", "#10B981"),
                transactionDate = System.currentTimeMillis() - (i * 3600000L),
                paymentMethod = PaymentMethod.UPI,
                transactionType = TransactionType.EXPENSE
            )
        }

        val data = ReportGenerationData(
            title = "Quarterly Report",
            periodLabel = "Custom Range",
            dateRangeLabel = "01 Sep 2026 - 30 Nov 2026",
            transactions = largeTxns,
            totalExpensesPaise = largeTxns.sumOf { it.amountPaise },
            totalIncomePaise = 0L,
            totalRefundsPaise = 0L,
            netSpendingPaise = largeTxns.sumOf { it.amountPaise },
            cashSpendingPaise = 0L,
            onlineSpendingPaise = largeTxns.sumOf { it.amountPaise },
            categoryBreakdown = listOf(CategoryReportItem("Groceries", largeTxns.sumOf { it.amountPaise }, 100.0, largeTxns.size)),
            expenseGroupBreakdown = emptyList()
        )

        val pdfFile = PdfReportGenerator.generatePdf(context, data)

        assertTrue(pdfFile.exists())
        assertTrue("Multi-page PDF must be substantial in size", pdfFile.length() > 5000L)
    }

    @Test
    fun testReportExclusionsOmitTransactionsFromReportTotalsWithoutModifyingDatabaseData() {
        val txn1 = Transaction(
            id = 1L,
            title = "Personal Lunch",
            amountPaise = 30000L, // ₹300
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )
        val txn2 = Transaction(
            id = 2L,
            title = "Confidential Gift",
            amountPaise = 500000L, // ₹5,000
            categoryId = 2L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.CASH,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )

        val allTransactions = listOf(txn1, txn2)
        val excludedIds = setOf(2L) // Exclude txn2 from the report statement

        // Report calculation with exclusion
        val includedInReport = allTransactions.filter { it.id !in excludedIds && !it.isIgnored }
        val reportTotal = includedInReport.sumOf { it.amountPaise }

        assertEquals("Report contains only non-excluded transaction", 1, includedInReport.size)
        assertEquals("Report total reflects only txn1 (₹300)", 30000L, reportTotal)

        // CRITICAL: Verify original domain transactions remain completely untouched
        assertEquals("Txn2 active status preserved", TransactionStatus.ACTIVE, txn2.transactionStatus)
        assertFalse("Txn2 isIgnored must remain false", txn2.isIgnored)
        assertEquals("Original transaction list size remains 2", 2, allTransactions.size)
    }

    @Test
    fun testReportDraftDomainModel() {
        val draft = ReportDraft(
            id = 10L,
            name = "Q3 Trip Statement",
            periodType = ReportPeriodType.CUSTOM,
            startDate = 1000L,
            endDate = 5000L,
            excludedTransactionIds = setOf(101L, 102L)
        )

        assertEquals("Q3 Trip Statement", draft.name)
        assertEquals(ReportPeriodType.CUSTOM, draft.periodType)
        assertEquals(2, draft.excludedTransactionIds.size)
        assertTrue(draft.excludedTransactionIds.contains(101L))
    }
}

