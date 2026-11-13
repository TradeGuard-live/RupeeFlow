package com.naman.rupeeflow.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DatePeriodHelper
import com.naman.rupeeflow.domain.util.DateTimeUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val allTransactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val searchQuery: String = "",
    val selectedTypeFilter: String = "ALL", // "ALL", "EXPENSE", "INCOME", "REFUND", "TRANSFER", "IGNORED"
    val selectedDateFilter: String = "ALL", // "ALL", "MONTHLY", "WEEKLY", "THIS_MONTH", "LAST_MONTH"
    val datePeriodOffset: Int = 0,
    val datePeriodLabel: String = "",
    val canNavigateNextDate: Boolean = false,
    val canNavigatePreviousDate: Boolean = true,
    val selectedPaymentMethodFilter: PaymentMethod? = null,
    val selectedCategoryFilter: Category? = null,
    val categories: List<Category> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val friendGroups: List<FriendGroup> = emptyList(),
    val expenseGroups: List<com.naman.rupeeflow.domain.model.ExpenseGroup> = emptyList()
)

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository,
    private val friendGroupRepository: FriendGroupRepository? = null,
    private val expenseGroupRepository: com.naman.rupeeflow.data.repository.ExpenseGroupRepository? = null,
    private val inputRankingRepository: InputRankingRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val groupsFlow = friendGroupRepository?.getAllGroups() ?: flowOf(emptyList())
            val expenseGroupsFlow = expenseGroupRepository?.getActiveExpenseGroups() ?: flowOf(emptyList())
            combine(
                transactionRepository.getAllTransactions(),
                categoryRepository.getAllCategories(),
                friendRepository.getActiveFriends(),
                groupsFlow,
                expenseGroupsFlow
            ) { txns, categories, friends, groups, expGroups ->
                GroupedTransactionBundle(txns, categories, friends, groups, expGroups)
            }.collect { bundle ->
                _uiState.update { current ->
                    val filtered = applyFilters(
                        txns = bundle.txns,
                        query = current.searchQuery,
                        typeFilter = current.selectedTypeFilter,
                        dateFilter = current.selectedDateFilter,
                        dateOffset = current.datePeriodOffset,
                        methodFilter = current.selectedPaymentMethodFilter,
                        categoryFilter = current.selectedCategoryFilter
                    )
                    val rankedCategories = inputRankingRepository?.rankOptions("CATEGORY", bundle.categories) { it.id.toString() } ?: bundle.categories
                    val rankedGroups = inputRankingRepository?.rankOptions("GROUP", bundle.groups) { it.id.toString() } ?: bundle.groups
                    val rankedFriends = inputRankingRepository?.rankOptions("FRIEND", bundle.friends) { it.id.toString() } ?: bundle.friends

                    current.copy(
                        isLoading = false,
                        allTransactions = bundle.txns,
                        filteredTransactions = filtered,
                        categories = rankedCategories,
                        friends = rankedFriends,
                        friendGroups = rankedGroups,
                        expenseGroups = bundle.expGroups
                    )
                }
            }
        }
    }

    fun createExpenseGroup(name: String) {
        viewModelScope.launch {
            expenseGroupRepository?.createExpenseGroup(name)
        }
    }

    private data class GroupedTransactionBundle(
        val txns: List<Transaction>,
        val categories: List<Category>,
        val friends: List<Friend>,
        val groups: List<FriendGroup>,
        val expGroups: List<com.naman.rupeeflow.domain.model.ExpenseGroup>
    )

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = query,
                typeFilter = current.selectedTypeFilter,
                dateFilter = current.selectedDateFilter,
                dateOffset = current.datePeriodOffset,
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = current.selectedCategoryFilter
            )
            current.copy(searchQuery = query, filteredTransactions = filtered)
        }
    }

    fun onTypeFilterSelected(filter: String) {
        _uiState.update { current ->
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = current.searchQuery,
                typeFilter = filter,
                dateFilter = current.selectedDateFilter,
                dateOffset = current.datePeriodOffset,
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = current.selectedCategoryFilter
            )
            current.copy(selectedTypeFilter = filter, filteredTransactions = filtered)
        }
    }

    fun onPaymentMethodFilterSelected(method: PaymentMethod?) {
        _uiState.update { current ->
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = current.searchQuery,
                typeFilter = current.selectedTypeFilter,
                dateFilter = current.selectedDateFilter,
                dateOffset = current.datePeriodOffset,
                methodFilter = method,
                categoryFilter = current.selectedCategoryFilter
            )
            current.copy(selectedPaymentMethodFilter = method, filteredTransactions = filtered)
        }
    }

    fun onCategoryFilterSelected(category: Category?) {
        _uiState.update { current ->
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = current.searchQuery,
                typeFilter = current.selectedTypeFilter,
                dateFilter = current.selectedDateFilter,
                dateOffset = current.datePeriodOffset,
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = category
            )
            current.copy(selectedCategoryFilter = category, filteredTransactions = filtered)
        }
    }

    fun onDateFilterSelected(filter: String) {
        _uiState.update { current ->
            val newOffset = 0
            val label = getDatePeriodLabel(filter, newOffset)
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = current.searchQuery,
                typeFilter = current.selectedTypeFilter,
                dateFilter = filter,
                dateOffset = newOffset,
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = current.selectedCategoryFilter
            )
            current.copy(
                selectedDateFilter = filter,
                datePeriodOffset = newOffset,
                datePeriodLabel = label,
                canNavigateNextDate = false,
                filteredTransactions = filtered
            )
        }
    }

    fun previousDatePeriod() {
        _uiState.update { current ->
            val newOffset = current.datePeriodOffset - 1
            val label = getDatePeriodLabel(current.selectedDateFilter, newOffset)
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = current.searchQuery,
                typeFilter = current.selectedTypeFilter,
                dateFilter = current.selectedDateFilter,
                dateOffset = newOffset,
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = current.selectedCategoryFilter
            )
            current.copy(
                datePeriodOffset = newOffset,
                datePeriodLabel = label,
                canNavigateNextDate = newOffset < 0,
                filteredTransactions = filtered
            )
        }
    }

    fun nextDatePeriod() {
        _uiState.update { current ->
            val newOffset = (current.datePeriodOffset + 1).coerceAtMost(0)
            val label = getDatePeriodLabel(current.selectedDateFilter, newOffset)
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = current.searchQuery,
                typeFilter = current.selectedTypeFilter,
                dateFilter = current.selectedDateFilter,
                dateOffset = newOffset,
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = current.selectedCategoryFilter
            )
            current.copy(
                datePeriodOffset = newOffset,
                datePeriodLabel = label,
                canNavigateNextDate = newOffset < 0,
                filteredTransactions = filtered
            )
        }
    }

    fun currentDatePeriod() {
        _uiState.update { current ->
            val newOffset = 0
            val label = getDatePeriodLabel(current.selectedDateFilter, newOffset)
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = current.searchQuery,
                typeFilter = current.selectedTypeFilter,
                dateFilter = current.selectedDateFilter,
                dateOffset = newOffset,
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = current.selectedCategoryFilter
            )
            current.copy(
                datePeriodOffset = newOffset,
                datePeriodLabel = label,
                canNavigateNextDate = false,
                filteredTransactions = filtered
            )
        }
    }

    private fun getDatePeriodLabel(filter: String, offset: Int): String {
        return when (filter) {
            "MONTHLY" -> DatePeriodHelper.getMonthRange(offset).label
            "WEEKLY" -> DatePeriodHelper.getWeekRange(offset).label
            else -> ""
        }
    }

    private fun applyFilters(
        txns: List<Transaction>,
        query: String,
        typeFilter: String,
        dateFilter: String = "ALL",
        dateOffset: Int = 0,
        methodFilter: PaymentMethod?,
        categoryFilter: Category?
    ): List<Transaction> {
        return txns.filter { txn ->
            // Search filter
            val matchesQuery = query.isBlank() ||
                    txn.title.contains(query, ignoreCase = true) ||
                    txn.description.contains(query, ignoreCase = true) ||
                    (txn.merchantName?.contains(query, ignoreCase = true) == true) ||
                    (txn.referenceNumber?.contains(query, ignoreCase = true) == true)

            // Status & Type filter
            val matchesType = when (typeFilter) {
                "ALL" -> txn.transactionStatus == TransactionStatus.ACTIVE
                "IGNORED" -> txn.transactionStatus == TransactionStatus.IGNORED
                "EXPENSE" -> txn.transactionStatus == TransactionStatus.ACTIVE && txn.transactionType == TransactionType.EXPENSE
                "INCOME" -> txn.transactionStatus == TransactionStatus.ACTIVE && txn.transactionType == TransactionType.INCOME
                "REFUND" -> txn.transactionStatus == TransactionStatus.ACTIVE && txn.transactionType == TransactionType.REFUND
                "TRANSFER" -> txn.transactionStatus == TransactionStatus.ACTIVE && txn.transactionType == TransactionType.TRANSFER
                else -> true
            }

            // Date period filter
            val matchesDate = when (dateFilter) {
                "MONTHLY" -> {
                    val range = DatePeriodHelper.getMonthRange(dateOffset)
                    txn.transactionDate >= range.startMillis && txn.transactionDate < range.endMillisExclusive
                }
                "WEEKLY" -> {
                    val range = DatePeriodHelper.getWeekRange(dateOffset)
                    txn.transactionDate >= range.startMillis && txn.transactionDate < range.endMillisExclusive
                }
                "THIS_MONTH" -> txn.transactionDate >= DateTimeUtils.getStartOfMonth()
                "LAST_MONTH" -> {
                    val range = DatePeriodHelper.getMonthRange(-1)
                    txn.transactionDate >= range.startMillis && txn.transactionDate < range.endMillisExclusive
                }
                else -> true
            }

            // Payment method
            val matchesMethod = methodFilter == null || txn.paymentMethod == methodFilter

            // Category
            val matchesCategory = categoryFilter == null || txn.categoryId == categoryFilter.id

            matchesQuery && matchesType && matchesDate && matchesMethod && matchesCategory
        }
    }

    fun saveTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.saveTransaction(transaction)
            inputRankingRepository?.let { ranking ->
                ranking.recordUsage("CATEGORY", transaction.categoryId.toString())
                ranking.recordUsage("PAYMENT_METHOD", transaction.paymentMethod.name)
                ranking.recordUsage("TRANSACTION_TYPE", transaction.transactionType.name)
                if (transaction.groupId != null) {
                    ranking.recordUsage("GROUP", transaction.groupId.toString())
                }
                if (transaction.paidByFriendId != null) {
                    ranking.recordUsage("FRIEND", transaction.paidByFriendId.toString())
                }
                transaction.participants.forEach { part ->
                    if (part.friendId != null) {
                        ranking.recordUsage("FRIEND", part.friendId.toString())
                    }
                }
            }
        }
    }

    fun recordSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementRepository.recordSettlement(settlement)
        }
    }

    fun toggleIgnore(transaction: Transaction) {
        viewModelScope.launch {
            val nextStatus = if (transaction.isIgnored) TransactionStatus.ACTIVE else TransactionStatus.IGNORED
            transactionRepository.updateTransactionStatus(transaction.id, nextStatus)
        }
    }

    fun duplicateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val copy = transaction.copy(
                id = 0,
                transactionDate = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
            transactionRepository.saveTransaction(copy)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            if (!transaction.billImagePath.isNullOrBlank()) {
                com.naman.rupeeflow.data.local.ImageStorageManager.deleteImage(transaction.billImagePath)
            }
            transactionRepository.deleteTransaction(transaction.id)
        }
    }
}
