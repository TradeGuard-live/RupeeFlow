package com.naman.rupeeflow.domain.ledger

import com.naman.rupeeflow.domain.model.DailySpending
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionStatus
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.DatePeriodHelper
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.domain.util.safeSumOf
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.TimeZone

/**
 * Diagnostic record representing reconciliation analysis for a transaction.
 * Strictly avoids logging personal data, merchant details, or sensitive financial information.
 */
data class TransactionReconciliationEntry(
    val transactionId: Long,
    val grossAmountPaise: Long,
    val personalSharePaise: Long,
    val isIncluded: Boolean,
    val refundAdjustmentPaise: Long,
    val expenseGroupId: Long?,
    val inclusionReason: String
)

/**
 * Authoritative financial aggregation service for personal share accounting.
 * Enforces unified inclusion and allocation rules across:
 * - Overview spending metrics
 * - Financial Analytics
 * - PDF Expense Reports
 * - Expense Group / Trip summaries
 * - Trend charts (Daily, Weekly, Monthly, Yearly)
 *
 * Rules:
 * - Non-split personal expenses: 100% of amount.
 * - Split expenses: Strictly the user's ("Me") participant share.
 * - Expense paid by Friend: User's allocated share is counted as personal expense.
 * - Expense paid by Me: Only user's share is counted; friends' shares are receivables, not personal spending.
 * - Equal split: Exact calculated/stored allocation for Me.
 * - Percentage split: Exact percentage allocation.
 * - Custom split: Stored custom allocation.
 * - Edited split: Latest saved participant allocation.
 * - Top-ups (UPI Lite funding): Excluded (0 paise) to prevent double counting with subsequent payments.
 * - Internal transfers: Excluded (0 paise).
 * - Ignored transactions: Excluded (0 paise).
 * - Refunds of split expenses: Allocated consistently with original personal share.
 * - Net personal spending: (Personal Expenses - Personal Refunds).coerceAtLeast(0).
 */
object PersonalFinancialAggregator {

    /**
     * Calculates the user's personal expense share for a single transaction.
     */
    fun calculatePersonalExpense(transaction: Transaction): Long {
        if (transaction.transactionStatus == TransactionStatus.IGNORED) return 0L
        if (transaction.transactionType != TransactionType.EXPENSE &&
            transaction.transactionType != TransactionType.UPI_LITE_PAYMENT
        ) return 0L

        return if (transaction.participants.isNotEmpty()) {
            val me = transaction.participants.firstOrNull { it.friendId == null }
            me?.shareAmountPaise ?: 0L
        } else {
            if (transaction.paidByFriendId == null) transaction.amountPaise else 0L
        }
    }

    /**
     * Calculates the user's personal income impact for a single transaction.
     * Excludes transfers and ignored transactions.
     */
    fun calculatePersonalIncome(transaction: Transaction): Long {
        if (transaction.transactionStatus == TransactionStatus.IGNORED) return 0L
        if (transaction.transactionType != TransactionType.INCOME) return 0L

        return if (transaction.participants.isNotEmpty()) {
            val me = transaction.participants.firstOrNull { it.friendId == null }
            me?.shareAmountPaise ?: transaction.amountPaise
        } else {
            transaction.amountPaise
        }
    }

    /**
     * Calculates the user's personal refund impact for a single transaction.
     * For shared refunds, only the user's participant share is counted as a personal refund.
     */
    fun calculatePersonalRefund(transaction: Transaction): Long {
        if (transaction.transactionStatus == TransactionStatus.IGNORED) return 0L
        if (transaction.transactionType != TransactionType.REFUND) return 0L

        return if (transaction.participants.isNotEmpty()) {
            val me = transaction.participants.firstOrNull { it.friendId == null }
            me?.shareAmountPaise ?: 0L
        } else {
            if (transaction.paidByFriendId == null) transaction.amountPaise else 0L
        }
    }

    /**
     * Sums personal expenses across a list of transactions using 64-bit integer paise arithmetic.
     */
    fun calculateTotalPersonalExpense(transactions: List<Transaction>): Long {
        return transactions.safeSumOf { calculatePersonalExpense(it) }
    }

    /**
     * Sums personal income across a list of transactions.
     */
    fun calculateTotalPersonalIncome(transactions: List<Transaction>): Long {
        return transactions.safeSumOf { calculatePersonalIncome(it) }
    }

    /**
     * Sums personal refunds across a list of transactions.
     */
    fun calculateTotalPersonalRefund(transactions: List<Transaction>): Long {
        return transactions.safeSumOf { calculatePersonalRefund(it) }
    }

    /**
     * Calculates net personal spending: (Personal Expenses - Personal Refunds).coerceAtLeast(0).
     */
    fun calculatePersonalNetSpending(transactions: List<Transaction>): Long {
        val totalExpense = calculateTotalPersonalExpense(transactions)
        val totalRefund = calculateTotalPersonalRefund(transactions)
        return SafeMath.safeSubtract(totalExpense, totalRefund).coerceAtLeast(0L)
    }

    /**
     * Calculates personal cash spending (strictly user's personal share of cash expenses).
     */
    fun calculatePersonalCashSpending(transactions: List<Transaction>): Long {
        return transactions
            .filter { it.paymentMethod == PaymentMethod.CASH }
            .safeSumOf { calculatePersonalExpense(it) }
    }

    /**
     * Calculates personal online / non-cash spending (strictly user's personal share of non-cash expenses).
     */
    fun calculatePersonalOnlineSpending(transactions: List<Transaction>): Long {
        return transactions
            .filter { it.paymentMethod != PaymentMethod.CASH }
            .safeSumOf { calculatePersonalExpense(it) }
    }

    /**
     * Calculates total gross bill amount (all participants combined) for informational reporting.
     */
    fun calculateGrossBillAmount(transactions: List<Transaction>): Long {
        return transactions
            .filter {
                it.transactionStatus == TransactionStatus.ACTIVE &&
                        (it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.UPI_LITE_PAYMENT)
            }
            .safeSumOf { it.amountPaise }
    }

    /**
     * Aggregates personal spending by Category ID.
     */
    fun calculatePersonalCategoryTotals(transactions: List<Transaction>): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        for (txn in transactions) {
            val personalExpense = calculatePersonalExpense(txn)
            if (personalExpense > 0L) {
                val current = result.getOrDefault(txn.categoryId, 0L)
                result[txn.categoryId] = SafeMath.safeAdd(current, personalExpense)
            }
        }
        return result
    }

    /**
     * Aggregates personal spending by Expense Group (Trip) ID.
     */
    fun calculatePersonalExpenseGroupTotals(transactions: List<Transaction>): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        for (txn in transactions) {
            val gId = txn.expenseGroupId ?: continue
            val personalExpense = calculatePersonalExpense(txn)
            if (personalExpense > 0L) {
                val current = result.getOrDefault(gId, 0L)
                result[gId] = SafeMath.safeAdd(current, personalExpense)
            }
        }
        return result
    }

    /**
     * Calculates personal daily trend points for a Monday-to-Sunday weekly period.
     */
    fun calculatePersonalDailyTotals(
        transactions: List<Transaction>,
        currentStartMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): List<DailySpending> {
        val refDate = Instant.ofEpochMilli(currentStartMillis).atZone(zoneId).toLocalDate()
        val monday = refDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return (0..6).map { i ->
            val dayDate = monday.plusDays(i.toLong())
            val dayStart = dayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayEndExclusive = dayDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            val dayTotal = transactions
                .filter {
                    it.transactionDate >= dayStart && it.transactionDate < dayEndExclusive
                }
                .safeSumOf { calculatePersonalExpense(it) }

            val dayLabel = dayDate.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            val dateRange = dayDate.format(DateTimeFormatter.ofPattern("d MMM", locale))
            DailySpending(dayStart, dayLabel, dayTotal, dateRange)
        }
    }

    /**
     * Calculates personal weekly trend points for a calendar month.
     */
    fun calculatePersonalWeeklyTotals(
        transactions: List<Transaction>,
        yearMonth: YearMonth,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): List<DailySpending> {
        val buckets = DatePeriodHelper.getMonthlyWeeks(yearMonth, timeZone, locale)

        return buckets.map { bucket ->
            val total = transactions
                .filter {
                    it.transactionDate >= bucket.calendarWeekStartMillis &&
                            it.transactionDate < bucket.calendarWeekEndMillisExclusive
                }
                .safeSumOf { calculatePersonalExpense(it) }

            DailySpending(
                timestamp = bucket.calendarWeekStartMillis,
                dayLabel = bucket.weekLabel,
                amountPaise = total,
                dateRangeLabel = bucket.dateRangeLabel
            )
        }
    }

    /**
     * Calculates personal monthly trend points for a calendar year.
     */
    fun calculatePersonalMonthlyTotals(
        transactions: List<Transaction>,
        year: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): List<DailySpending> {
        return (1..12).map { m ->
            val ym = YearMonth.of(year, m)
            val mStart = ym.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val mEndExclusive = ym.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            val mTotal = transactions
                .filter {
                    it.transactionDate >= mStart && it.transactionDate < mEndExclusive
                }
                .safeSumOf { calculatePersonalExpense(it) }

            val mLabel = ym.month.getDisplayName(TextStyle.SHORT, locale)
            val fullLabel = ym.month.getDisplayName(TextStyle.FULL, locale)
            DailySpending(mStart, mLabel, mTotal, fullLabel)
        }
    }

    /**
     * Calculates personal yearly trend points across given years.
     */
    fun calculatePersonalYearlyTotals(
        transactions: List<Transaction>,
        years: List<Int>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<DailySpending> {
        return years.map { year ->
            val yStart = LocalDate.of(year, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            val yEndExclusive = LocalDate.of(year + 1, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()

            val yTotal = transactions
                .filter {
                    it.transactionDate >= yStart && it.transactionDate < yEndExclusive
                }
                .safeSumOf { calculatePersonalExpense(it) }

            DailySpending(yStart, year.toString(), yTotal, year.toString())
        }
    }

    /**
     * Generates internal reconciliation diagnostic output for development and audit.
     * Strictly avoids logging personal data, merchant names, or sensitive user details.
     */
    fun generateReconciliationDiagnostics(
        transactions: List<Transaction>,
        excludedIds: Set<Long> = emptySet()
    ): List<TransactionReconciliationEntry> {
        return transactions.map { txn ->
            val isExcluded = excludedIds.contains(txn.id) ||
                    txn.transactionStatus == TransactionStatus.IGNORED ||
                    txn.transactionType == TransactionType.TRANSFER ||
                    txn.transactionType == TransactionType.UPI_LITE_TOPUP
            val personalShare = calculatePersonalExpense(txn)
            val refund = calculatePersonalRefund(txn)
            val reason = when {
                excludedIds.contains(txn.id) -> "USER_REPORT_EXCLUSION"
                txn.transactionStatus == TransactionStatus.IGNORED -> "IGNORED_STATUS"
                txn.transactionType == TransactionType.TRANSFER -> "TRANSFER_EXCLUDED"
                txn.transactionType == TransactionType.UPI_LITE_TOPUP -> "WALLET_TOPUP_EXCLUDED"
                txn.transactionType == TransactionType.REFUND -> "REFUND_ENTRY"
                txn.transactionType == TransactionType.INCOME -> "INCOME_ENTRY"
                txn.isSplit -> "SPLIT_EXPENSE_USER_SHARE"
                else -> "PERSONAL_EXPENSE"
            }
            TransactionReconciliationEntry(
                transactionId = txn.id,
                grossAmountPaise = txn.amountPaise,
                personalSharePaise = personalShare,
                isIncluded = !isExcluded,
                refundAdjustmentPaise = refund,
                expenseGroupId = txn.expenseGroupId,
                inclusionReason = reason
            )
        }
    }
}

