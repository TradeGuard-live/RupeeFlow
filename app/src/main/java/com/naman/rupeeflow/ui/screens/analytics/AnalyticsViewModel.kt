package com.naman.rupeeflow.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DatePeriodHelper
import com.naman.rupeeflow.domain.util.PeriodRange
import com.naman.rupeeflow.domain.util.PeriodType
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.domain.util.safeSumOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

private data class AnalyticsBaseBundle(
    val txns: List<Transaction>,
    val categories: List<Category>,
    val friends: List<Friend>,
    val settlements: List<Settlement>
)

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val selectedPeriod: String = "MONTHLY", // "WEEKLY", "MONTHLY", "YEARLY"
    val periodOffset: Int = 0,
    val periodInfo: String = "",
    val canNavigateNext: Boolean = false,
    val canNavigatePrevious: Boolean = true,
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

    private val selectedPeriodFlow = MutableStateFlow("MONTHLY")
    private val periodOffsetFlow = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            val baseFlow = combine(
                transactionRepository.getAllTransactions(),
                categoryRepository.getAllCategories(),
                friendRepository.getActiveFriends(),
                settlementRepository.getAllSettlements()
            ) { txns, categories, friends, settlements ->
                AnalyticsBaseBundle(txns, categories, friends, settlements)
            }

            combine(
                baseFlow,
                selectedPeriodFlow,
                periodOffsetFlow
            ) { bundle, period, offset ->
                val friendsWithLedger = LedgerCalculator.computeFriendLedgers(bundle.friends, bundle.txns, bundle.settlements)
                // Overflow-protected aggregations
                val owedToMe = friendsWithLedger.filter { it.netBalancePaise > 0L }.safeSumOf { it.netBalancePaise }
                val iOwe = friendsWithLedger.filter { it.netBalancePaise < 0L }.safeSumOf { SafeMath.safeAbs(it.netBalancePaise) }

                val (data, info) = computeAnalyticsForPeriod(bundle.txns, bundle.categories, period, offset)
                AnalyticsUiState(
                    isLoading = false,
                    selectedPeriod = period,
                    periodOffset = offset,
                    periodInfo = info,
                    canNavigateNext = offset < 0,
                    canNavigatePrevious = true,
                    analyticsData = data,
                    categories = bundle.categories,
                    totalOwedToMe = owedToMe,
                    totalIOwe = iOwe
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun setPeriod(period: String) {
        selectedPeriodFlow.value = period
        periodOffsetFlow.value = 0
    }

    fun previousPeriod() {
        periodOffsetFlow.update { it - 1 }
    }

    fun nextPeriod() {
        periodOffsetFlow.update { (it + 1).coerceAtMost(0) }
    }

    fun currentPeriod() {
        periodOffsetFlow.value = 0
    }

    private fun computeAnalyticsForPeriod(
        allTxns: List<Transaction>,
        categories: List<Category>,
        period: String,
        offset: Int
    ): Pair<AnalyticsData, String> {
        val periodType = when (period) {
            "WEEKLY" -> PeriodType.WEEK
            "YEARLY" -> PeriodType.YEAR
            else -> PeriodType.MONTH
        }

        val currentRange: PeriodRange = DatePeriodHelper.getRange(periodType, offset)
        val previousRange: PeriodRange = DatePeriodHelper.getRange(periodType, offset - 1)

        val currentStart = currentRange.startMillis
        val currentEnd = currentRange.endMillis
        val previousStart = previousRange.startMillis
        val previousEnd = previousRange.endMillis

        // Active transactions in current period
        val currentTxns = allTxns.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    it.transactionDate in currentStart..currentEnd
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

        // Net spending accounting for refunds
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
                val daysList = mutableListOf<DailySpending>()
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                for (i in 0..6) {
                    val dCal = Calendar.getInstance().apply {
                        timeInMillis = currentStart
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
                Pair(daysList, currentRange.label)
            }
            "YEARLY" -> {
                val calYear = Calendar.getInstance().apply {
                    timeInMillis = currentStart
                }
                val currentYear = calYear.get(Calendar.YEAR)

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
                Pair(monthList, currentRange.label)
            }
            else -> { // "MONTHLY"
                val calMonth = Calendar.getInstance().apply {
                    timeInMillis = currentStart
                }
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
                        timeInMillis = currentStart
                        set(Calendar.DAY_OF_MONTH, startDay)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val wEndCal = Calendar.getInstance().apply {
                        timeInMillis = currentStart
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
                Pair(weekList, currentRange.label)
            }
        }

        val now = System.currentTimeMillis()
        val effectiveEnd = if (currentEnd > now) now else currentEnd
        val daysCount = kotlin.math.max(1, ((effectiveEnd - currentStart) / (1000 * 60 * 60 * 24)).toInt() + 1)
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
