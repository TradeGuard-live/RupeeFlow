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

data class ExpenseGroupTransactionCluster(
    val key: String,
    val groupId: Long?,
    val name: String,
    val iconName: String? = null,
    val colorHex: String? = null,
    val transactions: List<Transaction>,
    val totalPersonalExpensePaise: Long,
    val transactionCount: Int,
    val dateRangeLabel: String,
    val isExpanded: Boolean = false
)

sealed interface TransactionListItem {
    data class Individual(val transaction: Transaction) : TransactionListItem {
        val latestDate: Long get() = transaction.transactionDate
    }

    data class TripCard(
        val expenseGroupId: Long,
        val name: String,
        val destination: String? = null,
        val iconName: String? = null,
        val colorHex: String? = null,
        val transactionCount: Int,
        val totalPersonalExpensePaise: Long,
        val dateRangeLabel: String,
        val latestDate: Long,
        val matchingTransactions: List<Transaction>
    ) : TransactionListItem
}

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val allTransactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val listItems: List<TransactionListItem> = emptyList(),
    val isGroupedByExpenseGroup: Boolean = true,
    val expandedGroupKeys: Set<String> = emptySet(),
    val transactionClusters: List<ExpenseGroupTransactionCluster> = emptyList(),
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
    val expenseGroups: List<com.naman.rupeeflow.domain.model.ExpenseGroup> = emptyList(),
    val pendingCount: Int = 0
)

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository,
    private val friendGroupRepository: FriendGroupRepository? = null,
    private val expenseGroupRepository: com.naman.rupeeflow.data.repository.ExpenseGroupRepository? = null,
    private val inputRankingRepository: InputRankingRepository? = null,
    private val pendingTransactionRepository: PendingTransactionRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        if (pendingTransactionRepository != null) {
            viewModelScope.launch {
                pendingTransactionRepository.getPendingTransactions().collect { pending ->
                    _uiState.update { it.copy(pendingCount = pending.size) }
                }
            }
        }
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
                    val clusters = clusterTransactions(filtered, bundle.expGroups, current.expandedGroupKeys)
                    val listItems = buildTransactionListItems(filtered, bundle.expGroups)
                    val rankedCategories = inputRankingRepository?.rankOptions("CATEGORY", bundle.categories) { it.id.toString() } ?: bundle.categories
                    val rankedGroups = inputRankingRepository?.rankOptions("GROUP", bundle.groups) { it.id.toString() } ?: bundle.groups
                    val rankedFriends = inputRankingRepository?.rankOptions("FRIEND", bundle.friends) { it.id.toString() } ?: bundle.friends

                    current.copy(
                        isLoading = false,
                        allTransactions = bundle.txns,
                        filteredTransactions = filtered,
                        listItems = listItems,
                        transactionClusters = clusters,
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

    fun setGroupingMode(isGrouped: Boolean) {
        _uiState.update { it.copy(isGroupedByExpenseGroup = isGrouped) }
    }

    fun toggleGroupExpansion(key: String) {
        _uiState.update { current ->
            val updatedKeys = if (current.expandedGroupKeys.contains(key)) {
                current.expandedGroupKeys - key
            } else {
                current.expandedGroupKeys + key
            }
            val updatedClusters = current.transactionClusters.map { cluster ->
                if (cluster.key == key) cluster.copy(isExpanded = updatedKeys.contains(key)) else cluster
            }
            current.copy(expandedGroupKeys = updatedKeys, transactionClusters = updatedClusters)
        }
    }

    fun expandAllGroups() {
        _uiState.update { current ->
            val allKeys = current.transactionClusters.map { it.key }.toSet()
            val updatedClusters = current.transactionClusters.map { it.copy(isExpanded = true) }
            current.copy(expandedGroupKeys = allKeys, transactionClusters = updatedClusters)
        }
    }

    fun collapseAllGroups() {
        _uiState.update { current ->
            val updatedClusters = current.transactionClusters.map { it.copy(isExpanded = false) }
            current.copy(expandedGroupKeys = emptySet(), transactionClusters = updatedClusters)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { updateFilterState(it, query = query) }
    }

    fun onTypeFilterSelected(filter: String) {
        _uiState.update { updateFilterState(it, typeFilter = filter) }
    }

    fun onPaymentMethodFilterSelected(method: PaymentMethod?) {
        _uiState.update { updateFilterState(it, methodFilter = method) }
    }

    fun onCategoryFilterSelected(category: Category?) {
        _uiState.update { updateFilterState(it, categoryFilter = category) }
    }

    fun onDateFilterSelected(filter: String) {
        _uiState.update { updateFilterState(it, dateFilter = filter, dateOffset = 0) }
    }

    fun previousDatePeriod() {
        _uiState.update { updateFilterState(it, dateOffset = it.datePeriodOffset - 1) }
    }

    fun nextDatePeriod() {
        _uiState.update { updateFilterState(it, dateOffset = (it.datePeriodOffset + 1).coerceAtMost(0)) }
    }

    fun currentDatePeriod() {
        _uiState.update { updateFilterState(it, dateOffset = 0) }
    }

    private fun updateFilterState(
        current: TransactionsUiState,
        query: String = current.searchQuery,
        typeFilter: String = current.selectedTypeFilter,
        dateFilter: String = current.selectedDateFilter,
        dateOffset: Int = current.datePeriodOffset,
        methodFilter: PaymentMethod? = current.selectedPaymentMethodFilter,
        categoryFilter: Category? = current.selectedCategoryFilter
    ): TransactionsUiState {
        val filtered = applyFilters(
            txns = current.allTransactions,
            query = query,
            typeFilter = typeFilter,
            dateFilter = dateFilter,
            dateOffset = dateOffset,
            methodFilter = methodFilter,
            categoryFilter = categoryFilter
        )
        val expandedKeys = if (query.isNotBlank()) {
            val matchingKeys = filtered.mapNotNull { txn ->
                if (txn.expenseGroupId != null) "group_${txn.expenseGroupId}"
                else if (!txn.expenseGroupName.isNullOrBlank()) "named_${txn.expenseGroupName}"
                else "ungrouped"
            }.toSet()
            current.expandedGroupKeys + matchingKeys
        } else {
            current.expandedGroupKeys
        }
        val clusters = clusterTransactions(filtered, current.expenseGroups, expandedKeys)
        val listItems = buildTransactionListItems(filtered, current.expenseGroups)
        val dateLabel = getDatePeriodLabel(dateFilter, dateOffset)

        return current.copy(
            searchQuery = query,
            selectedTypeFilter = typeFilter,
            selectedDateFilter = dateFilter,
            datePeriodOffset = dateOffset,
            datePeriodLabel = dateLabel,
            canNavigateNextDate = dateOffset < 0,
            selectedPaymentMethodFilter = methodFilter,
            selectedCategoryFilter = categoryFilter,
            filteredTransactions = filtered,
            listItems = listItems,
            transactionClusters = clusters,
            expandedGroupKeys = expandedKeys
        )
    }

    private fun buildTransactionListItems(
        txns: List<Transaction>,
        expenseGroups: List<com.naman.rupeeflow.domain.model.ExpenseGroup>
    ): List<TransactionListItem> {
        if (txns.isEmpty()) return emptyList()

        val groupMap = expenseGroups.associateBy { it.id }
        val (tripTxns, ungroupedTxns) = txns.partition { it.expenseGroupId != null }

        val items = mutableListOf<TransactionListItem>()

        for (txn in ungroupedTxns) {
            items.add(TransactionListItem.Individual(txn))
        }

        val groupedByTrip = tripTxns.groupBy { it.expenseGroupId!! }
        for ((groupId, groupTransactions) in groupedByTrip) {
            val groupObj = groupMap[groupId]
            val first = groupTransactions.first()
            val name = groupObj?.name ?: first.expenseGroupName ?: "Trip"
            val destination = groupObj?.destination
            val iconName = groupObj?.iconName ?: "flight"
            val colorHex = groupObj?.colorHex ?: "#3B82F6"
            val sortedTxns = groupTransactions.sortedByDescending { it.transactionDate }
            val latestDate = sortedTxns.first().transactionDate
            val minDate = sortedTxns.last().transactionDate

            val dateLabel = if (groupObj?.startDate != null && groupObj.endDate != null) {
                "${DateTimeUtils.formatDate(groupObj.startDate!!, "dd MMM")} – ${DateTimeUtils.formatDate(groupObj.endDate!!, "dd MMM yyyy")}"
            } else if (minDate == latestDate) {
                DateTimeUtils.formatDate(minDate, "dd MMM yyyy")
            } else {
                "${DateTimeUtils.formatDate(minDate, "dd MMM")} – ${DateTimeUtils.formatDate(latestDate, "dd MMM yyyy")}"
            }

            val totalPersonal = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculateTotalPersonalExpense(sortedTxns)

            items.add(
                TransactionListItem.TripCard(
                    expenseGroupId = groupId,
                    name = name,
                    destination = destination,
                    iconName = iconName,
                    colorHex = colorHex,
                    transactionCount = sortedTxns.size,
                    totalPersonalExpensePaise = totalPersonal,
                    dateRangeLabel = dateLabel,
                    latestDate = latestDate,
                    matchingTransactions = sortedTxns
                )
            )
        }

        return items.sortedByDescending { item ->
            when (item) {
                is TransactionListItem.Individual -> item.transaction.transactionDate
                is TransactionListItem.TripCard -> item.latestDate
            }
        }
    }

    private fun clusterTransactions(
        txns: List<Transaction>,
        expenseGroups: List<com.naman.rupeeflow.domain.model.ExpenseGroup>,
        expandedKeys: Set<String>
    ): List<ExpenseGroupTransactionCluster> {
        if (txns.isEmpty()) return emptyList()

        val groupMap = expenseGroups.associateBy { it.id }

        val (groupedTxns, ungroupedTxns) = txns.partition {
            it.expenseGroupId != null || !it.expenseGroupName.isNullOrBlank()
        }

        val clusters = mutableListOf<ExpenseGroupTransactionCluster>()

        val groupedByGroup = groupedTxns.groupBy { txn ->
            if (txn.expenseGroupId != null) "group_${txn.expenseGroupId}"
            else "named_${txn.expenseGroupName}"
        }

        for ((key, groupTxns) in groupedByGroup) {
            val first = groupTxns.first()
            val groupObj = first.expenseGroupId?.let { groupMap[it] }
            val name = groupObj?.name ?: first.expenseGroupName ?: "Expense Group"
            val iconName = groupObj?.iconName ?: "flight"
            val colorHex = groupObj?.colorHex ?: "#3B82F6"
            val sortedTxns = groupTxns.sortedByDescending { it.transactionDate }

            val totalPersonal = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculateTotalPersonalExpense(sortedTxns)
            val minDate = sortedTxns.minOf { it.transactionDate }
            val maxDate = sortedTxns.maxOf { it.transactionDate }
            val dateLabel = if (minDate == maxDate) {
                DateTimeUtils.formatDate(minDate, "dd MMM yyyy")
            } else {
                "${DateTimeUtils.formatDate(minDate, "dd MMM")} – ${DateTimeUtils.formatDate(maxDate, "dd MMM yyyy")}"
            }

            clusters.add(
                ExpenseGroupTransactionCluster(
                    key = key,
                    groupId = first.expenseGroupId,
                    name = name,
                    iconName = iconName,
                    colorHex = colorHex,
                    transactions = sortedTxns,
                    totalPersonalExpensePaise = totalPersonal,
                    transactionCount = sortedTxns.size,
                    dateRangeLabel = dateLabel,
                    isExpanded = expandedKeys.contains(key)
                )
            )
        }

        if (ungroupedTxns.isNotEmpty()) {
            val sortedUngrouped = ungroupedTxns.sortedByDescending { it.transactionDate }
            val totalPersonal = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculateTotalPersonalExpense(sortedUngrouped)
            val minDate = sortedUngrouped.minOf { it.transactionDate }
            val maxDate = sortedUngrouped.maxOf { it.transactionDate }
            val dateLabel = if (minDate == maxDate) {
                DateTimeUtils.formatDate(minDate, "dd MMM yyyy")
            } else {
                "${DateTimeUtils.formatDate(minDate, "dd MMM")} – ${DateTimeUtils.formatDate(maxDate, "dd MMM yyyy")}"
            }

            clusters.add(
                ExpenseGroupTransactionCluster(
                    key = "ungrouped",
                    groupId = null,
                    name = "Other Expenses",
                    iconName = "receipt_long",
                    colorHex = "#64748B",
                    transactions = sortedUngrouped,
                    totalPersonalExpensePaise = totalPersonal,
                    transactionCount = sortedUngrouped.size,
                    dateRangeLabel = dateLabel,
                    isExpanded = expandedKeys.contains("ungrouped")
                )
            )
        }

        return clusters.sortedByDescending { cluster ->
            cluster.transactions.firstOrNull()?.transactionDate ?: 0L
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
                    (txn.referenceNumber?.contains(query, ignoreCase = true) == true) ||
                    (txn.expenseGroupName?.contains(query, ignoreCase = true) == true)

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
