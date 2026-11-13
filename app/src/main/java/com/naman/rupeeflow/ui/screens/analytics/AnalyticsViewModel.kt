package com.naman.rupeeflow.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.AnalyticsData
import com.naman.rupeeflow.domain.model.Category
import com.naman.rupeeflow.domain.model.CategorySpending
import com.naman.rupeeflow.domain.model.DailySpending
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionStatus
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.DatePeriodHelper
import com.naman.rupeeflow.domain.util.PeriodRange
import com.naman.rupeeflow.domain.util.PeriodType
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.domain.util.safeSumOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        val currentEndExclusive = currentRange.endMillisExclusive
        val previousStart = previousRange.startMillis
        val previousEndExclusive = previousRange.endMillisExclusive

        // Active transactions strictly within the current period using half-open interval [start, endExclusive)
        val currentTxns = allTxns.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    it.transactionDate >= currentStart &&
                    it.transactionDate < currentEndExclusive
        }

        // Previous period net spending
        val prevTxns = allTxns.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    it.transactionDate >= previousStart &&
                    it.transactionDate < previousEndExclusive
        }
        val prevSpent = PersonalFinancialAggregator.calculatePersonalNetSpending(prevTxns)

        val totalSpent = PersonalFinancialAggregator.calculateTotalPersonalExpense(currentTxns)
        val totalIncome = PersonalFinancialAggregator.calculateTotalPersonalIncome(currentTxns)
        val totalRefund = PersonalFinancialAggregator.calculateTotalPersonalRefund(currentTxns)
        val cashSpending = PersonalFinancialAggregator.calculatePersonalCashSpending(currentTxns)
        val onlineSpending = PersonalFinancialAggregator.calculatePersonalOnlineSpending(currentTxns)
        val categoryMap = PersonalFinancialAggregator.calculatePersonalCategoryTotals(currentTxns)

        // Net spending accounting for refunds
        val netSpending = PersonalFinancialAggregator.calculatePersonalNetSpending(currentTxns)
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

        // Trend points calculation using consistent Monday-to-Sunday boundaries
        val zoneId = ZoneId.systemDefault()
        val locale = Locale.getDefault()

        val (trendPoints, periodInfo) = when (period) {
            "WEEKLY" -> {
                val trend = PersonalFinancialAggregator.calculatePersonalDailyTotals(
                    transactions = currentTxns,
                    currentStartMillis = currentStart,
                    zoneId = zoneId,
                    locale = locale
                )
                Pair(trend, currentRange.label)
            }
            "YEARLY" -> {
                val year = Instant.ofEpochMilli(currentStart).atZone(zoneId).toLocalDate().year
                val trend = PersonalFinancialAggregator.calculatePersonalMonthlyTotals(
                    transactions = currentTxns,
                    year = year,
                    zoneId = zoneId,
                    locale = locale
                )
                Pair(trend, currentRange.label)
            }
            else -> { // "MONTHLY"
                val yearMonth = YearMonth.from(Instant.ofEpochMilli(currentStart).atZone(zoneId))
                val trend = PersonalFinancialAggregator.calculatePersonalWeeklyTotals(
                    transactions = currentTxns,
                    yearMonth = yearMonth,
                    timeZone = TimeZone.getDefault(),
                    locale = locale
                )
                Pair(trend, currentRange.label)
            }
        }

        val daysInPeriod = when (period) {
            "WEEKLY" -> 7
            "YEARLY" -> {
                val year = Instant.ofEpochMilli(currentStart).atZone(zoneId).toLocalDate().year
                if (java.time.Year.of(year).isLeap) 366 else 365
            }
            else -> {
                val ym = YearMonth.from(Instant.ofEpochMilli(currentStart).atZone(zoneId))
                ym.lengthOfMonth()
            }
        }
        val dailyAvg = if (daysInPeriod > 0) totalSpent / daysInPeriod else 0L

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
