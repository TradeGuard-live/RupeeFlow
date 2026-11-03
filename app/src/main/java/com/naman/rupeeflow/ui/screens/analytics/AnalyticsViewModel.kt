package com.naman.rupeeflow.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.domain.util.safeSumOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val selectedPeriod: String = "MONTHLY", // "WEEKLY", "MONTHLY", "YEARLY"
    val periodInfo: String = "",
    val analyticsData: AnalyticsData = AnalyticsData(),
    val categories: List<Category> = emptyList(),
    val totalOwedToMe: Long = 0L,
    val totalIOwe: Long = 0L
)

class AnalyticsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            combine(
                transactionRepository.getAllTransactions(),
                categoryRepository.getAllCategories(),
                friendRepository.getActiveFriends(),
                settlementRepository.getAllSettlements(),
                _uiState.map { it.selectedPeriod }.distinctUntilChanged()
            ) { txns, categories, friends, settlements, period ->
                val friendsWithLedger = LedgerCalculator.computeFriendLedgers(friends, txns, settlements)
                // ✅ Overflow-protected aggregations
                val owedToMe = friendsWithLedger.filter { it.netBalancePaise > 0L }.safeSumOf { it.netBalancePaise }
                val iOwe = friendsWithLedger.filter { it.netBalancePaise < 0L }.safeSumOf { SafeMath.safeAbs(it.netBalancePaise) }

                val (data, info) = computeAnalyticsForPeriod(txns, categories, period)
                AnalyticsUiState(
                    isLoading = false,
                    selectedPeriod = period,
                    periodInfo = info,
                    analyticsData = data,
                    categories = categories,
                    totalOwedToMe = owedToMe,
                    totalIOwe = iOwe
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setPeriod(period: String) {
        _uiState.update { it.copy(selectedPeriod = period) }
    }

    private fun computeAnalyticsForPeriod(
        allTxns: List<Transaction>,
        categories: List<Category>,
        period: String
    ): Pair<AnalyticsData, String> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        val (currentStart, previousStart, previousEnd) = when (period) {
            "WEEKLY" -> {
                val startWeek = DateTimeUtils.getStartOfWeek(now)
                val prevStart = startWeek - (7 * 24 * 60 * 60 * 1000L)
                val prevEnd = startWeek - 1
                Triple(startWeek, prevStart, prevEnd)
            }
            "YEARLY" -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startYear = cal.timeInMillis

                val prevCal = Calendar.getInstance().apply {
                    timeInMillis = startYear
                    add(Calendar.YEAR, -1)
                }
                Triple(startYear, prevCal.timeInMillis, startYear - 1)
            }
            else -> { // "MONTHLY"
                val startMonth = DateTimeUtils.getStartOfMonth(now)
                val prevMonthCal = Calendar.getInstance().apply {
                    timeInMillis = startMonth
                    add(Calendar.MONTH, -1)
                }
                Triple(startMonth, prevMonthCal.timeInMillis, startMonth - 1)
            }
        }

        // Active transactions in current period
        val currentTxns = allTxns.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    it.transactionDate >= currentStart &&
                    it.transactionDate <= now
        }

        // Previous period transactions
        val prevExpTxns = allTxns.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    it.transactionDate in previousStart..previousEnd &&
                    it.transactionType == TransactionType.EXPENSE
        }
        val prevRefundTxns = allTxns.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    it.transactionDate in previousStart..previousEnd &&
                    it.transactionType == TransactionType.REFUND
        }
        val prevGrossSpent = prevExpTxns.sumOf { it.userExpensePaise }
        val prevRefunds = prevRefundTxns.sumOf { it.amountPaise }
        val prevSpent = (prevGrossSpent - prevRefunds).coerceAtLeast(0L)

        var totalSpent = 0L
        var totalIncome = 0L
        var totalRefund = 0L
        var cashSpending = 0L
        var onlineSpending = 0L
        val categoryMap = mutableMapOf<Long, Long>()

        for (t in currentTxns) {
            when (t.transactionType) {
                TransactionType.EXPENSE -> {
                    val userExpense = t.userExpensePaise
                    totalSpent += userExpense
                    if (t.paymentMethod == PaymentMethod.CASH) {
                        cashSpending += userExpense
                    } else {
                        onlineSpending += userExpense
                    }
                    val catTotal = categoryMap.getOrDefault(t.categoryId, 0L)
                    categoryMap[t.categoryId] = catTotal + userExpense
                }
                TransactionType.INCOME -> {
                    totalIncome += t.amountPaise
                }
                TransactionType.REFUND -> {
                    totalRefund += t.amountPaise
                }
                else -> {}
            }
        }

        // ✅ Net spending accounting for refunds
        val netSpending = (totalSpent - totalRefund).coerceAtLeast(0L)
        val netSavings = totalIncome - netSpending
        val savingsRate = if (totalIncome > 0L) {
            ((netSavings.toFloat() / totalIncome.toFloat()) * 100f).coerceIn(-100f, 100f)
        } else 0f

        // Category breakdown
        val catLookup = categories.associateBy { it.id }
        val categoryBreakdown = categoryMap.mapNotNull { (catId, amount) ->
            val cat = catLookup[catId] ?: Category(catId, "Other", "category", "#94A3B8")
            val pct = if (totalSpent > 0L) (amount.toFloat() / totalSpent.toFloat()) * 100f else 0f
            CategorySpending(cat, amount, pct, 1)
        }.sortedByDescending { it.totalSpentPaise }

        val highestCategory = categoryBreakdown.firstOrNull()?.category

        // Trend points calculation based on requested groupings:
        // Monthly: Week-wise breakdown ("Week 1", "Week 2", etc.)
        // Yearly: Month-wise breakdown ("Jan", "Feb", etc.)
        // Weekly: Day-wise breakdown ("Mon", "Tue", etc.)
        val (trendPoints, periodInfo) = when (period) {
            "WEEKLY" -> {
                val calWeek = Calendar.getInstance()
                val dow = calWeek.get(Calendar.DAY_OF_WEEK)
                val daysFromMon = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                calWeek.add(Calendar.DAY_OF_YEAR, -daysFromMon)
                calWeek.set(Calendar.HOUR_OF_DAY, 0)
                calWeek.set(Calendar.MINUTE, 0)
                calWeek.set(Calendar.SECOND, 0)
                calWeek.set(Calendar.MILLISECOND, 0)

                val monStart = calWeek.timeInMillis
                val sunEnd = monStart + (7 * 24 * 60 * 60 * 1000L) - 1

                val df = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                val info = "${df.format(java.util.Date(monStart))} - ${df.format(java.util.Date(sunEnd))}"

                val daysList = mutableListOf<DailySpending>()
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                for (i in 0..6) {
                    val dCal = Calendar.getInstance().apply {
                        timeInMillis = monStart
                        add(Calendar.DAY_OF_YEAR, i)
                    }
                    val sOfDay = dCal.timeInMillis
                    val eOfDay = sOfDay + (24 * 60 * 60 * 1000L) - 1

                    val total = allTxns
                        .filter {
                            it.transactionStatus == TransactionStatus.ACTIVE &&
                                    it.transactionType == TransactionType.EXPENSE &&
                                    it.transactionDate in sOfDay..eOfDay
                        }
                        .sumOf { it.userExpensePaise }
                    daysList.add(DailySpending(sOfDay, dayNames[i], total))
                }
                Pair(daysList, info)
            }
            "YEARLY" -> {
                val calYear = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val currentYear = calYear.get(Calendar.YEAR)
                val info = "Year $currentYear"

                val monthList = mutableListOf<DailySpending>()
                val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                for (m in 0..11) {
                    val mCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, currentYear)
                        set(Calendar.MONTH, m)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val mStart = mCal.timeInMillis
                    val maxDay = mCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    mCal.set(Calendar.DAY_OF_MONTH, maxDay)
                    mCal.set(Calendar.HOUR_OF_DAY, 23)
                    mCal.set(Calendar.MINUTE, 59)
                    mCal.set(Calendar.SECOND, 59)
                    mCal.set(Calendar.MILLISECOND, 999)
                    val mEnd = mCal.timeInMillis

                    val total = allTxns
                        .filter {
                            it.transactionStatus == TransactionStatus.ACTIVE &&
                                    it.transactionType == TransactionType.EXPENSE &&
                                    it.transactionDate in mStart..mEnd
                        }
                        .sumOf { it.userExpensePaise }

                    monthList.add(DailySpending(mStart, monthNames[m], total))
                }
                Pair(monthList, info)
            }
            else -> { // "MONTHLY"
                val calMonth = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val monthDf = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                val info = monthDf.format(calMonth.time)

                val maxDaysInMonth = calMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
                val weekList = mutableListOf<DailySpending>()

                val weekRanges = mutableListOf<Pair<Int, Int>>()
                weekRanges.add(1 to 7)
                weekRanges.add(8 to 14)
                weekRanges.add(15 to 21)
                weekRanges.add(22 to 28)
                if (maxDaysInMonth > 28) {
                    weekRanges.add(29 to maxDaysInMonth)
                }

                weekRanges.forEachIndexed { idx, (startDay, endDay) ->
                    val wStartCal = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, startDay)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val wEndCal = Calendar.getInstance().apply {
                        set(Calendar.DAY_OF_MONTH, endDay)
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }

                    val wStart = wStartCal.timeInMillis
                    val wEnd = wEndCal.timeInMillis

                    val total = allTxns
                        .filter {
                            it.transactionStatus == TransactionStatus.ACTIVE &&
                                    it.transactionType == TransactionType.EXPENSE &&
                                    it.transactionDate in wStart..wEnd
                        }
                        .sumOf { it.userExpensePaise }

                    weekList.add(DailySpending(wStart, "Week ${idx + 1}", total))
                }
                Pair(weekList, info)
            }
        }

        val daysCount = kotlin.math.max(1, ((now - currentStart) / (1000 * 60 * 60 * 24)).toInt() + 1)
        val dailyAvg = netSpending / daysCount

        val pctChange = if (prevSpent > 0L) {
            ((netSpending - prevSpent).toFloat() / prevSpent.toFloat()) * 100f
        } else 0f

        val analyticsData = AnalyticsData(
            totalSpentPaise = totalSpent,
            totalIncomePaise = totalIncome,
            totalRefundPaise = totalRefund,
            netSpendingPaise = netSpending,
            totalSavingsPaise = netSavings.coerceAtLeast(0L),
            savingsRate = savingsRate,
            cashSpendingPaise = cashSpending,
            onlineSpendingPaise = onlineSpending,
            dailyAveragePaise = dailyAvg,
            highestCategory = highestCategory,
            categoryBreakdown = categoryBreakdown,
            dailySpendingTrend = trendPoints,
            previousPeriodSpentPaise = prevSpent,
            percentageChangeVsPrevious = pctChange
        )
        return Pair(analyticsData, periodInfo)
    }
}
