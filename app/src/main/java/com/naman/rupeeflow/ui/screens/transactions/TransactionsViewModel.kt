package com.naman.rupeeflow.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.model.*
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
    val selectedDateFilter: String = "ALL", // "ALL", "THIS_MONTH", "LAST_MONTH"
    val selectedPaymentMethodFilter: PaymentMethod? = null,
    val selectedCategoryFilter: Category? = null,
    val categories: List<Category> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val friendGroups: List<FriendGroup> = emptyList()
)

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository,
    private val friendGroupRepository: FriendGroupRepository? = null,
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
            combine(
                transactionRepository.getAllTransactions(),
                categoryRepository.getAllCategories(),
                friendRepository.getActiveFriends(),
                groupsFlow
            ) { txns, categories, friends, groups ->
                GroupedTransactionBundle(txns, categories, friends, groups)
            }.collect { bundle ->
                _uiState.update { current ->
                    val filtered = applyFilters(
                        txns = bundle.txns,
                        query = current.searchQuery,
                        typeFilter = current.selectedTypeFilter,
                        dateFilter = current.selectedDateFilter,
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
                        friendGroups = rankedGroups
                    )
                }
            }
        }
    }

    private data class GroupedTransactionBundle(
        val txns: List<Transaction>,
        val categories: List<Category>,
        val friends: List<Friend>,
        val groups: List<FriendGroup>
    )

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = query,
                typeFilter = current.selectedTypeFilter,
                dateFilter = current.selectedDateFilter,
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
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = category
            )
            current.copy(selectedCategoryFilter = category, filteredTransactions = filtered)
        }
    }

    fun onDateFilterSelected(filter: String) {
        _uiState.update { current ->
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = current.searchQuery,
                typeFilter = current.selectedTypeFilter,
                dateFilter = filter,
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = current.selectedCategoryFilter
            )
            current.copy(selectedDateFilter = filter, filteredTransactions = filtered)
        }
    }

    private fun applyFilters(
        txns: List<Transaction>,
        query: String,
        typeFilter: String,
        dateFilter: String = "ALL",
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
                "THIS_MONTH" -> txn.transactionDate >= DateTimeUtils.getStartOfMonth()
                "LAST_MONTH" -> {
                    val startThisMonth = DateTimeUtils.getStartOfMonth()
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = startThisMonth
                        add(Calendar.MONTH, -1)
                    }
                    txn.transactionDate in cal.timeInMillis until startThisMonth
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
