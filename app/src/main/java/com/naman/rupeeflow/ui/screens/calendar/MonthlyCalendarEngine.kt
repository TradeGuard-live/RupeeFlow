package com.naman.rupeeflow.ui.screens.calendar

import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionStatus
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.SafeMath
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class TransactionsViewMode {
    LIST,
    CALENDAR
}

data class CalendarDaySummary(
    val dayOfMonth: Int,
    val dateMillis: Long,
    val totalExpensePaise: Long = 0L,
    val totalIncomePaise: Long = 0L,
    val totalRefundPaise: Long = 0L,
    val totalTransferPaise: Long = 0L,
    val transactionCount: Int = 0,
    val transactions: List<Transaction> = emptyList()
) {
    val hasTransactions: Boolean get() = transactionCount > 0
    val netCashflowPaise: Long get() = totalIncomePaise + totalRefundPaise - totalExpensePaise
}

data class CalendarGridCell(
    val dayOfMonth: Int?, // null for blank leading or trailing slots
    val dateMillis: Long?,
    val isCurrentMonth: Boolean,
    val summary: CalendarDaySummary? = null
)

data class MonthlyCalendarGridData(
    val year: Int,
    val month: Int, // 0-based Calendar.MONTH (0 = January, 11 = December)
    val monthTitle: String,
    val daysInMonth: Int,
    val leadingBlankCount: Int,
    val trailingBlankCount: Int,
    val totalRowCount: Int,
    val cells: List<CalendarGridCell>,
    val totalMonthExpensePaise: Long = 0L,
    val totalMonthIncomePaise: Long = 0L,
    val totalMonthRefundPaise: Long = 0L
)

object MonthlyCalendarEngine {

    fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    }

    fun getDaysInMonth(year: Int, month: Int): Int {
        return when (month) {
            Calendar.JANUARY, Calendar.MARCH, Calendar.MAY, Calendar.JULY,
            Calendar.AUGUST, Calendar.OCTOBER, Calendar.DECEMBER -> 31
            Calendar.APRIL, Calendar.JUNE, Calendar.SEPTEMBER, Calendar.NOVEMBER -> 30
            Calendar.FEBRUARY -> if (isLeapYear(year)) 29 else 28
            else -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
        }
    }

    fun getLeadingBlankCount(
        year: Int,
        month: Int,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): Int {
        val cal = Calendar.getInstance(timeZone, locale).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        // Monday is first day of week:
        // Sunday (1) -> 6, Monday (2) -> 0, Tuesday (3) -> 1, ..., Saturday (7) -> 5
        return (dow - Calendar.MONDAY + 7) % 7
    }

    fun getMonthTitle(
        year: Int,
        month: Int,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val cal = Calendar.getInstance(timeZone, locale).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val sdf = SimpleDateFormat("MMMM yyyy", locale).apply {
            this.timeZone = timeZone
        }
        return sdf.format(cal.time)
    }

    fun getFormattedDateLabel(
        dateMillis: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy", locale).apply {
            this.timeZone = timeZone
        }
        return sdf.format(Date(dateMillis))
    }

    fun getPreviousMonth(year: Int, month: Int): Pair<Int, Int> {
        return if (month == Calendar.JANUARY) {
            Pair(year - 1, Calendar.DECEMBER)
        } else {
            Pair(year, month - 1)
        }
    }

    fun getNextMonth(year: Int, month: Int): Pair<Int, Int> {
        return if (month == Calendar.DECEMBER) {
            Pair(year + 1, Calendar.JANUARY)
        } else {
            Pair(year, month + 1)
        }
    }

    fun computeDailySummaries(
        transactions: List<Transaction>,
        year: Int,
        month: Int,
        selectedTypeFilter: String = "ALL",
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): Map<Int, CalendarDaySummary> {
        val cal = Calendar.getInstance(timeZone, locale)
        val dayMap = mutableMapOf<Int, MutableList<Transaction>>()

        for (txn in transactions) {
            // Respect ignored transactions rule:
            // Only include ignored transactions if filter is specifically IGNORED
            if (txn.isIgnored && selectedTypeFilter != "IGNORED") {
                continue
            }
            if (!txn.isIgnored && selectedTypeFilter == "IGNORED") {
                continue
            }

            cal.timeInMillis = txn.transactionDate
            if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                dayMap.getOrPut(day) { mutableListOf() }.add(txn)
            }
        }

        val daysInMonth = getDaysInMonth(year, month)
        val calDay = Calendar.getInstance(timeZone, locale).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val result = mutableMapOf<Int, CalendarDaySummary>()

        for (d in 1..daysInMonth) {
            calDay.set(Calendar.DAY_OF_MONTH, d)
            val dayStart = calDay.timeInMillis
            val dayEndExclusive = Calendar.getInstance(timeZone, locale).apply {
                timeInMillis = dayStart
                add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis

            val dayTxns = dayMap[d] ?: emptyList()

            var expenseTotal = 0L
            var incomeTotal = 0L
            var refundTotal = 0L
            var transferTotal = 0L

            for (txn in dayTxns) {
                val activeTxn = if (txn.isIgnored) txn.copy(transactionStatus = TransactionStatus.ACTIVE) else txn
                when (txn.transactionType) {
                    TransactionType.EXPENSE, TransactionType.UPI_LITE_PAYMENT -> {
                        val share = PersonalFinancialAggregator.calculatePersonalExpense(activeTxn)
                        expenseTotal = SafeMath.safeAdd(expenseTotal, share)
                    }
                    TransactionType.INCOME -> {
                        val share = PersonalFinancialAggregator.calculatePersonalIncome(activeTxn)
                        incomeTotal = SafeMath.safeAdd(incomeTotal, share)
                    }
                    TransactionType.REFUND, TransactionType.UPI_LITE_REFUND, TransactionType.UPI_LITE_REVERSAL -> {
                        val share = PersonalFinancialAggregator.calculatePersonalRefund(activeTxn)
                        refundTotal = SafeMath.safeAdd(refundTotal, share)
                    }
                    TransactionType.TRANSFER, TransactionType.UPI_LITE_TOPUP -> {
                        transferTotal = SafeMath.safeAdd(transferTotal, txn.amountPaise)
                    }
                    else -> {}
                }
            }

            val sortedTxns = dayTxns.sortedWith(Comparator { a, b ->
                if (a.transactionDate != b.transactionDate) b.transactionDate.compareTo(a.transactionDate)
                else b.id.compareTo(a.id)
            })

            result[d] = CalendarDaySummary(
                dayOfMonth = d,
                dateMillis = dayStart,
                totalExpensePaise = expenseTotal,
                totalIncomePaise = incomeTotal,
                totalRefundPaise = refundTotal,
                totalTransferPaise = transferTotal,
                transactionCount = dayTxns.size,
                transactions = sortedTxns
            )
        }

        return result
    }

    fun computeMonthGrid(
        year: Int,
        month: Int,
        transactions: List<Transaction>,
        selectedTypeFilter: String = "ALL",
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): MonthlyCalendarGridData {
        val daysInMonth = getDaysInMonth(year, month)
        val leadingBlanks = getLeadingBlankCount(year, month, timeZone, locale)
        val totalDaysWithBlanks = leadingBlanks + daysInMonth
        val trailingBlanks = (7 - (totalDaysWithBlanks % 7)) % 7
        val totalCells = totalDaysWithBlanks + trailingBlanks
        val totalRows = totalCells / 7

        val dailySummaries = computeDailySummaries(
            transactions = transactions,
            year = year,
            month = month,
            selectedTypeFilter = selectedTypeFilter,
            timeZone = timeZone,
            locale = locale
        )

        val cellList = mutableListOf<CalendarGridCell>()

        // Leading blank cells (from previous month)
        val (prevYear, prevMonth) = getPreviousMonth(year, month)
        val daysInPrevMonth = getDaysInMonth(prevYear, prevMonth)
        for (i in 0 until leadingBlanks) {
            val prevDay = daysInPrevMonth - leadingBlanks + 1 + i
            cellList.add(
                CalendarGridCell(
                    dayOfMonth = prevDay,
                    dateMillis = null,
                    isCurrentMonth = false,
                    summary = null
                )
            )
        }

        // Current month cells
        for (d in 1..daysInMonth) {
            val summary = dailySummaries[d]
            cellList.add(
                CalendarGridCell(
                    dayOfMonth = d,
                    dateMillis = summary?.dateMillis,
                    isCurrentMonth = true,
                    summary = summary
                )
            )
        }

        // Trailing blank cells (for next month)
        for (i in 1..trailingBlanks) {
            cellList.add(
                CalendarGridCell(
                    dayOfMonth = i,
                    dateMillis = null,
                    isCurrentMonth = false,
                    summary = null
                )
            )
        }

        var monthExpense = 0L
        var monthIncome = 0L
        var monthRefund = 0L
        for ((_, summary) in dailySummaries) {
            monthExpense = SafeMath.safeAdd(monthExpense, summary.totalExpensePaise)
            monthIncome = SafeMath.safeAdd(monthIncome, summary.totalIncomePaise)
            monthRefund = SafeMath.safeAdd(monthRefund, summary.totalRefundPaise)
        }

        return MonthlyCalendarGridData(
            year = year,
            month = month,
            monthTitle = getMonthTitle(year, month, locale, timeZone),
            daysInMonth = daysInMonth,
            leadingBlankCount = leadingBlanks,
            trailingBlankCount = trailingBlanks,
            totalRowCount = totalRows,
            cells = cellList,
            totalMonthExpensePaise = monthExpense,
            totalMonthIncomePaise = monthIncome,
            totalMonthRefundPaise = monthRefund
        )
    }

    fun isSameDay(
        millis1: Long,
        millis2: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val cal1 = Calendar.getInstance(timeZone).apply { timeInMillis = millis1 }
        val cal2 = Calendar.getInstance(timeZone).apply { timeInMillis = millis2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
