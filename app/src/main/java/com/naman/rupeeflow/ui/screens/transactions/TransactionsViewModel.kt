package com.naman.rupeeflow.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val allTransactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val searchQuery: String = "",
    val selectedTypeFilter: String = "ALL", // "ALL", "EXPENSE", "INCOME", "TRANSFER", "IGNORED"
    val selectedPaymentMethodFilter: PaymentMethod? = null,
    val selectedCategoryFilter: Category? = null,
    val categories: List<Category> = emptyList(),
    val friends: List<Friend> = emptyList()
)

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                transactionRepository.getAllTransactions(),
                categoryRepository.getAllCategories(),
                friendRepository.getActiveFriends()
            ) { txns, categories, friends ->
                Triple(txns, categories, friends)
            }.collect { (txns, categories, friends) ->
                _uiState.update { current ->
                    val filtered = applyFilters(
                        txns = txns,
                        query = current.searchQuery,
                        typeFilter = current.selectedTypeFilter,
                        methodFilter = current.selectedPaymentMethodFilter,
                        categoryFilter = current.selectedCategoryFilter
                    )
                    current.copy(
                        isLoading = false,
                        allTransactions = txns,
                        filteredTransactions = filtered,
                        categories = categories,
                        friends = friends
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilters(
                txns = current.allTransactions,
                query = query,
                typeFilter = current.selectedTypeFilter,
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
                methodFilter = current.selectedPaymentMethodFilter,
                categoryFilter = category
            )
            current.copy(selectedCategoryFilter = category, filteredTransactions = filtered)
        }
    }

    private fun applyFilters(
        txns: List<Transaction>,
        query: String,
        typeFilter: String,
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
                "INCOME" -> txn.transactionStatus == TransactionStatus.ACTIVE && (txn.transactionType == TransactionType.INCOME || txn.transactionType == TransactionType.REFUND)
                "TRANSFER" -> txn.transactionStatus == TransactionStatus.ACTIVE && txn.transactionType == TransactionType.TRANSFER
                else -> true
            }

            // Payment method
            val matchesMethod = methodFilter == null || txn.paymentMethod == methodFilter

            // Category
            val matchesCategory = categoryFilter == null || txn.categoryId == categoryFilter.id

            matchesQuery && matchesType && matchesMethod && matchesCategory
        }
    }

    fun saveTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.saveTransaction(transaction)
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
            transactionRepository.deleteTransaction(transaction.id)
        }
    }
}
