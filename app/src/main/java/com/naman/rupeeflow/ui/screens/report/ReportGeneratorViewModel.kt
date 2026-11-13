package com.naman.rupeeflow.ui.screens.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.ExpenseGroupRepository
import com.naman.rupeeflow.data.repository.ReportDraftRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.model.Category
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.ReportDraft
import com.naman.rupeeflow.domain.model.ReportPeriodType
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.report.CategoryReportItem
import com.naman.rupeeflow.report.ExpenseGroupReportItem
import com.naman.rupeeflow.report.PdfReportGenerator
import com.naman.rupeeflow.report.ReportGenerationData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.*
import java.time.temporal.TemporalAdjusters

data class ReportGeneratorUiState(
    val isLoading: Boolean = true,
    val periodType: ReportPeriodType = ReportPeriodType.MONTHLY,
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val selectedWeekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val customStartDate: Long? = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    val customEndDate: Long? = LocalDate.now().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    val targetExpenseGroupId: Long? = null,
    val isTripReport: Boolean = false,
    val tripName: String? = null,
    val tripDestination: String? = null,
    val excludedTransactionIds: Set<Long> = emptySet(),
    val rawTransactionsInRange: List<Transaction> = emptyList(),
    val includedTransactions: List<Transaction> = emptyList(),
    val totalExpensesPaise: Long = 0L,
    val totalIncomePaise: Long = 0L,
    val totalRefundsPaise: Long = 0L,
    val netSpendingPaise: Long = 0L,
    val cashSpendingPaise: Long = 0L,
    val onlineSpendingPaise: Long = 0L,
    val grossBillsPaise: Long = 0L,
    val categoryBreakdown: List<CategoryReportItem> = emptyList(),
    val expenseGroupBreakdown: List<ExpenseGroupReportItem> = emptyList(),
    val savedDrafts: List<ReportDraft> = emptyList(),
    val generatedPdfFile: File? = null,
    val isGeneratingPdf: Boolean = false,
    val errorMessage: String? = null
) {
    val isCustomRangeIncomplete: Boolean
        get() = periodType == ReportPeriodType.CUSTOM && (customStartDate == null || customEndDate == null)
}

class ReportGeneratorViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val reportDraftRepository: ReportDraftRepository,
    private val expenseGroupRepository: ExpenseGroupRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportGeneratorUiState())
    val uiState: StateFlow<ReportGeneratorUiState> = _uiState.asStateFlow()

    private var allTransactions: List<Transaction> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                transactionRepository.getAllTransactions(),
                reportDraftRepository.getAllDrafts()
            ) { txns, drafts ->
                Pair(txns, drafts)
            }.collect { (txns, drafts) ->
                allTransactions = txns
                _uiState.update { current ->
                    recomputeMetrics(current.copy(savedDrafts = drafts, isLoading = false))
                }
            }
        }
    }

    fun initTripReport(groupId: Long) {
        viewModelScope.launch {
            val group = expenseGroupRepository?.getExpenseGroupById(groupId)
            val txns = if (allTransactions.isNotEmpty()) allTransactions else transactionRepository.getAllTransactions().first()
            val tripTxns = txns.filter { it.expenseGroupId == groupId }
            val (tripStart, tripEnd) = when {
                group?.startDate != null && group.endDate != null -> {
                    val zone = ZoneId.systemDefault()
                    val s = Instant.ofEpochMilli(group.startDate!!).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                    val e = Instant.ofEpochMilli(group.endDate!!).atZone(zone).toLocalDate().atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                    Pair(s, e)
                }
                tripTxns.isNotEmpty() -> {
                    val zone = ZoneId.systemDefault()
                    val minT = tripTxns.minOf { it.transactionDate }
                    val maxT = tripTxns.maxOf { it.transactionDate }
                    val s = Instant.ofEpochMilli(minT).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                    val e = Instant.ofEpochMilli(maxT).atZone(zone).toLocalDate().atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                    Pair(s, e)
                }
                else -> {
                    val now = LocalDate.now()
                    val zone = ZoneId.systemDefault()
                    val s = now.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant().toEpochMilli()
                    val e = now.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                    Pair(s, e)
                }
            }

            _uiState.update { current ->
                recomputeMetrics(
                    current.copy(
                        targetExpenseGroupId = groupId,
                        isTripReport = true,
                        tripName = group?.name,
                        tripDestination = group?.destination,
                        periodType = ReportPeriodType.CUSTOM,
                        customStartDate = tripStart,
                        customEndDate = tripEnd,
                        generatedPdfFile = null
                    )
                )
            }
        }
    }

    fun setPeriodType(type: ReportPeriodType) {
        _uiState.update { current ->
            recomputeMetrics(current.copy(periodType = type, generatedPdfFile = null))
        }
    }

    fun setCustomStartDate(startMillis: Long?) {
        val current = _uiState.value
        if (startMillis != null && current.customEndDate != null && startMillis > current.customEndDate) {
            _uiState.update { it.copy(errorMessage = "Start date must be before or equal to end date") }
            return
        }
        _uiState.update { curr ->
            recomputeMetrics(
                curr.copy(
                    customStartDate = startMillis,
                    errorMessage = null,
                    generatedPdfFile = null
                )
            )
        }
    }

    fun setCustomEndDate(endMillis: Long?) {
        val current = _uiState.value
        if (endMillis != null && current.customStartDate != null && endMillis < current.customStartDate) {
            _uiState.update { it.copy(errorMessage = "End date cannot be earlier than start date") }
            return
        }
        _uiState.update { curr ->
            recomputeMetrics(
                curr.copy(
                    customEndDate = endMillis,
                    errorMessage = null,
                    generatedPdfFile = null
                )
            )
        }
    }

    fun clearCustomDates() {
        _uiState.update { curr ->
            recomputeMetrics(
                curr.copy(
                    customStartDate = null,
                    customEndDate = null,
                    errorMessage = null,
                    generatedPdfFile = null
                )
            )
        }
    }

    fun setCustomDateRange(startMillis: Long?, endMillis: Long?) {
        if (startMillis != null && endMillis != null && startMillis > endMillis) {
            _uiState.update { it.copy(errorMessage = "Start date must be before or equal to end date") }
            return
        }
        _uiState.update { current ->
            recomputeMetrics(
                current.copy(
                    customStartDate = startMillis,
                    customEndDate = endMillis,
                    errorMessage = null,
                    generatedPdfFile = null
                )
            )
        }
    }

    fun selectMonth(year: Int, month: Int) {
        _uiState.update { current ->
            recomputeMetrics(current.copy(selectedYear = year, selectedMonth = month, generatedPdfFile = null))
        }
    }

    fun selectYear(year: Int) {
        _uiState.update { current ->
            recomputeMetrics(current.copy(selectedYear = year, generatedPdfFile = null))
        }
    }

    fun selectWeek(mondayStart: LocalDate) {
        _uiState.update { current ->
            recomputeMetrics(current.copy(selectedWeekStart = mondayStart, generatedPdfFile = null))
        }
    }

    fun toggleTransactionExclusion(transactionId: Long) {
        _uiState.update { current ->
            val updatedExclusions = if (current.excludedTransactionIds.contains(transactionId)) {
                current.excludedTransactionIds - transactionId
            } else {
                current.excludedTransactionIds + transactionId
            }
            recomputeMetrics(current.copy(excludedTransactionIds = updatedExclusions, generatedPdfFile = null))
        }
    }

    fun setAllTransactionsExcluded(excludeAll: Boolean) {
        _uiState.update { current ->
            val updatedExclusions = if (excludeAll) {
                current.rawTransactionsInRange.map { it.id }.toSet()
            } else {
                emptySet()
            }
            recomputeMetrics(current.copy(excludedTransactionIds = updatedExclusions, generatedPdfFile = null))
        }
    }

    fun saveDraft(draftName: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val (startMillis, endMillis) = computeDateBounds(state)
            val draft = ReportDraft(
                name = draftName.trim().ifBlank { "Report Draft ${DateTimeUtils.formatDate(System.currentTimeMillis(), "dd MMM")}" },
                periodType = state.periodType,
                startDate = startMillis,
                endDate = endMillis,
                excludedTransactionIds = state.excludedTransactionIds
            )
            reportDraftRepository.saveDraft(draft)
        }
    }

    fun loadDraft(draft: ReportDraft) {
        _uiState.update { current ->
            recomputeMetrics(
                current.copy(
                    periodType = draft.periodType,
                    customStartDate = draft.startDate,
                    customEndDate = draft.endDate,
                    excludedTransactionIds = draft.excludedTransactionIds,
                    generatedPdfFile = null
                )
            )
        }
    }

    fun deleteDraft(draftId: Long) {
        viewModelScope.launch {
            reportDraftRepository.deleteDraft(draftId)
        }
    }

    fun generatePdf(context: Context) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isCustomRangeIncomplete) {
                _uiState.update { it.copy(errorMessage = "Select both start and end dates.") }
                return@launch
            }

            _uiState.update { it.copy(isGeneratingPdf = true, errorMessage = null) }
            try {
                val (startMillis, endMillis) = computeDateBounds(state)
                val dateRangeLabel = "${DateTimeUtils.formatDate(startMillis, "dd MMM yyyy")} - ${DateTimeUtils.formatDate(endMillis, "dd MMM yyyy")}"
                val title = if (state.isTripReport) {
                    "${state.tripName ?: "Trip"} Statement"
                } else {
                    when (state.periodType) {
                        ReportPeriodType.WEEKLY -> "Weekly Financial Statement"
                        ReportPeriodType.MONTHLY -> "Monthly Financial Statement"
                        ReportPeriodType.YEARLY -> "Annual Financial Statement"
                        ReportPeriodType.CUSTOM -> "Expense Report"
                    }
                }
                val periodLabel = if (state.isTripReport) {
                    "Trip: ${state.tripName ?: "Trip"}"
                } else {
                    state.periodType.displayName
                }

                val data = ReportGenerationData(
                    title = title,
                    periodLabel = periodLabel,
                    dateRangeLabel = dateRangeLabel,
                    transactions = state.includedTransactions,
                    totalExpensesPaise = state.totalExpensesPaise,
                    totalIncomePaise = state.totalIncomePaise,
                    totalRefundsPaise = state.totalRefundsPaise,
                    netSpendingPaise = state.netSpendingPaise,
                    cashSpendingPaise = state.cashSpendingPaise,
                    onlineSpendingPaise = state.onlineSpendingPaise,
                    grossBillsPaise = state.grossBillsPaise,
                    categoryBreakdown = state.categoryBreakdown,
                    expenseGroupBreakdown = state.expenseGroupBreakdown
                )

                val file = PdfReportGenerator.generatePdf(context, data)
                _uiState.update { it.copy(generatedPdfFile = file, isGeneratingPdf = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingPdf = false, errorMessage = "Failed to generate PDF: ${e.message}") }
            }
        }
    }

    private fun computeDateBounds(state: ReportGeneratorUiState): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        return when (state.periodType) {
            ReportPeriodType.WEEKLY -> {
                val start = state.selectedWeekStart.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = state.selectedWeekStart.plusDays(6).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
            ReportPeriodType.MONTHLY -> {
                val ym = YearMonth.of(state.selectedYear, state.selectedMonth)
                val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = ym.atEndOfMonth().atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
            ReportPeriodType.YEARLY -> {
                val start = LocalDate.of(state.selectedYear, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = LocalDate.of(state.selectedYear, 12, 31).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
                Pair(start, end)
            }
            ReportPeriodType.CUSTOM -> {
                val start = state.customStartDate ?: 0L
                val end = state.customEndDate ?: Long.MAX_VALUE
                Pair(start, end)
            }
        }
    }

    private fun recomputeMetrics(state: ReportGeneratorUiState): ReportGeneratorUiState {
        // If this is a trip report with targetExpenseGroupId, filter strictly to this trip permanently!
        val baseTxns = if (state.targetExpenseGroupId != null) {
            allTransactions.filter { it.expenseGroupId == state.targetExpenseGroupId }
        } else {
            allTransactions
        }

        if (state.isCustomRangeIncomplete) {
            return state.copy(
                rawTransactionsInRange = emptyList(),
                includedTransactions = emptyList(),
                totalExpensesPaise = 0L,
                totalIncomePaise = 0L,
                totalRefundsPaise = 0L,
                netSpendingPaise = 0L,
                cashSpendingPaise = 0L,
                onlineSpendingPaise = 0L,
                grossBillsPaise = 0L,
                categoryBreakdown = emptyList(),
                expenseGroupBreakdown = emptyList()
            )
        }

        val (startMillis, endMillis) = computeDateBounds(state)

        // Filter transactions strictly within date range
        val inRange = baseTxns.filter { txn ->
            txn.transactionDate in startMillis..endMillis
        }

        // Apply report exclusions (never modifies actual database data!)
        val included = inRange.filter { txn ->
            !state.excludedTransactionIds.contains(txn.id) && !txn.isIgnored
        }

        val totalExpensesPaise = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculateTotalPersonalExpense(included)
        val totalIncomePaise = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculateTotalPersonalIncome(included)
        val totalRefundsPaise = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculateTotalPersonalRefund(included)
        val netSpendingPaise = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculatePersonalNetSpending(included)
        val cashSpendingPaise = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculatePersonalCashSpending(included)
        val onlineSpendingPaise = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculatePersonalOnlineSpending(included)
        val grossBillsPaise = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculateGrossBillAmount(included)

        // Category breakdown using personal shares
        val categoryBreakdown = included
            .filter { com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculatePersonalExpense(it) > 0L }
            .groupBy { it.category?.name ?: "Other" }
            .map { (catName, txns) ->
                val sumPaise = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculateTotalPersonalExpense(txns)
                val pct = if (totalExpensesPaise > 0L) (sumPaise.toDouble() / totalExpensesPaise.toDouble()) * 100.0 else 0.0
                CategoryReportItem(
                    categoryName = catName,
                    amountPaise = sumPaise,
                    percentage = pct,
                    transactionCount = txns.size
                )
            }.sortedByDescending { it.amountPaise }

        // Expense group breakdown using personal shares
        val expenseGroupBreakdown = included
            .filter { it.hasExpenseGroup && com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculatePersonalExpense(it) > 0L }
            .groupBy { it.expenseGroupName ?: "Group" }
            .map { (groupName, txns) ->
                val sumPaise = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculateTotalPersonalExpense(txns)
                ExpenseGroupReportItem(
                    groupName = groupName,
                    amountPaise = sumPaise,
                    transactionCount = txns.size
                )
            }.sortedByDescending { it.amountPaise }

        return state.copy(
            rawTransactionsInRange = inRange,
            includedTransactions = included,
            totalExpensesPaise = totalExpensesPaise,
            totalIncomePaise = totalIncomePaise,
            totalRefundsPaise = totalRefundsPaise,
            netSpendingPaise = netSpendingPaise,
            cashSpendingPaise = cashSpendingPaise,
            onlineSpendingPaise = onlineSpendingPaise,
            grossBillsPaise = grossBillsPaise,
            categoryBreakdown = categoryBreakdown,
            expenseGroupBreakdown = expenseGroupBreakdown
        )
    }
}
