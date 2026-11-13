package com.naman.rupeeflow.ui.screens.report

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
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
    val customStartDate: Long = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    val customEndDate: Long = LocalDate.now().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    val excludedTransactionIds: Set<Long> = emptySet(),
    val rawTransactionsInRange: List<Transaction> = emptyList(),
    val includedTransactions: List<Transaction> = emptyList(),
    val totalExpensesPaise: Long = 0L,
    val totalIncomePaise: Long = 0L,
    val totalRefundsPaise: Long = 0L,
    val netSpendingPaise: Long = 0L,
    val cashSpendingPaise: Long = 0L,
    val onlineSpendingPaise: Long = 0L,
    val categoryBreakdown: List<CategoryReportItem> = emptyList(),
    val expenseGroupBreakdown: List<ExpenseGroupReportItem> = emptyList(),
    val savedDrafts: List<ReportDraft> = emptyList(),
    val generatedPdfFile: File? = null,
    val isGeneratingPdf: Boolean = false,
    val errorMessage: String? = null
)

class ReportGeneratorViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val reportDraftRepository: ReportDraftRepository
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

    fun setPeriodType(type: ReportPeriodType) {
        _uiState.update { current ->
            recomputeMetrics(current.copy(periodType = type, generatedPdfFile = null))
        }
    }

    fun setCustomDateRange(startMillis: Long, endMillis: Long) {
        if (startMillis > endMillis) {
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
            _uiState.update { it.copy(isGeneratingPdf = true, errorMessage = null) }
            try {
                val state = _uiState.value
                val (startMillis, endMillis) = computeDateBounds(state)
                val dateRangeLabel = "${DateTimeUtils.formatDate(startMillis, "dd MMM yyyy")} - ${DateTimeUtils.formatDate(endMillis, "dd MMM yyyy")}"
                val title = when (state.periodType) {
                    ReportPeriodType.WEEKLY -> "Weekly Financial Statement"
                    ReportPeriodType.MONTHLY -> "Monthly Financial Statement"
                    ReportPeriodType.YEARLY -> "Annual Financial Statement"
                    ReportPeriodType.CUSTOM -> "Expense Report"
                }

                val data = ReportGenerationData(
                    title = title,
                    periodLabel = state.periodType.displayName,
                    dateRangeLabel = dateRangeLabel,
                    transactions = state.includedTransactions,
                    totalExpensesPaise = state.totalExpensesPaise,
                    totalIncomePaise = state.totalIncomePaise,
                    totalRefundsPaise = state.totalRefundsPaise,
                    netSpendingPaise = state.netSpendingPaise,
                    cashSpendingPaise = state.cashSpendingPaise,
                    onlineSpendingPaise = state.onlineSpendingPaise,
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
                Pair(state.customStartDate, state.customEndDate)
            }
        }
    }

    private fun recomputeMetrics(state: ReportGeneratorUiState): ReportGeneratorUiState {
        val (startMillis, endMillis) = computeDateBounds(state)

        // Filter transactions strictly within date range
        val inRange = allTransactions.filter { txn ->
            txn.transactionDate in startMillis..endMillis
        }

        // Apply report exclusions (never modifies actual database data!)
        val included = inRange.filter { txn ->
            !state.excludedTransactionIds.contains(txn.id) && !txn.isIgnored
        }

        val expenses = included.filter { it.transactionType == TransactionType.EXPENSE }
        val income = included.filter { it.transactionType == TransactionType.INCOME }
        val refunds = included.filter { it.transactionType == TransactionType.REFUND }

        val totalExpensesPaise = expenses.sumOf { it.amountPaise }
        val totalIncomePaise = income.sumOf { it.amountPaise }
        val totalRefundsPaise = refunds.sumOf { it.amountPaise }
        val netSpendingPaise = (totalExpensesPaise - totalRefundsPaise - totalIncomePaise).coerceAtLeast(0L)

        val cashSpendingPaise = expenses.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.amountPaise }
        val onlineSpendingPaise = expenses.filter { it.paymentMethod != PaymentMethod.CASH }.sumOf { it.amountPaise }

        // Category breakdown
        val categoryBreakdown = expenses.groupBy { it.category?.name ?: "Other" }.map { (catName, txns) ->
            val sumPaise = txns.sumOf { it.amountPaise }
            val pct = if (totalExpensesPaise > 0L) (sumPaise.toDouble() / totalExpensesPaise.toDouble()) * 100.0 else 0.0
            CategoryReportItem(
                categoryName = catName,
                amountPaise = sumPaise,
                percentage = pct,
                transactionCount = txns.size
            )
        }.sortedByDescending { it.amountPaise }

        // Expense group breakdown
        val expenseGroupBreakdown = expenses.filter { it.hasExpenseGroup }.groupBy { it.expenseGroupName ?: "Group" }.map { (groupName, txns) ->
            ExpenseGroupReportItem(
                groupName = groupName,
                amountPaise = txns.sumOf { it.amountPaise },
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
            categoryBreakdown = categoryBreakdown,
            expenseGroupBreakdown = expenseGroupBreakdown
        )
    }
}

