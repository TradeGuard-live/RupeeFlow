package com.naman.rupeeflow.ui.screens.splitpro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.SafeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SplitProUiState(
    val isLoading: Boolean = true,
    val groups: List<ExpenseGroup> = emptyList(),
    val filteredGroups: List<ExpenseGroup> = emptyList(),
    val selectedGroupType: GroupType? = null,
    val showArchived: Boolean = false,
    val searchQuery: String = "",
    val friends: List<Friend> = emptyList(),
    val categories: List<Category> = emptyList(),
    val allTransactions: List<Transaction> = emptyList(),
    val allSettlements: List<Settlement> = emptyList(),
    val recentActivities: List<GroupActivity> = emptyList(),
    val userPreferences: UserPreferences = UserPreferences(),
    val totalGroupsCount: Int = 0,
    val totalGroupExpensesPaise: Long = 0L,
    val totalOthersOweMePaise: Long = 0L,
    val totalIOweOthersPaise: Long = 0L,
    val upcomingSettlementsCount: Int = 0,
    val groupSummaries: Map<Long, GroupBalanceSummary> = emptyMap()
)

class SplitProViewModel(
    private val expenseGroupRepository: ExpenseGroupRepository,
    private val transactionRepository: TransactionRepository,
    private val settlementRepository: SettlementRepository,
    private val friendRepository: FriendRepository,
    private val friendGroupRepository: FriendGroupRepository,
    private val categoryRepository: CategoryRepository,
    private val groupActivityRepository: GroupActivityRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplitProUiState())
    val uiState: StateFlow<SplitProUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private data class CoreDataBundle(
        val groups: List<ExpenseGroup>,
        val txns: List<Transaction>,
        val settlements: List<Settlement>,
        val friends: List<Friend>
    )

    private data class MetaDataBundle(
        val cats: List<Category>,
        val activities: List<GroupActivity>,
        val prefs: UserPreferences
    )

    private fun loadData() {
        val coreFlow = combine(
            expenseGroupRepository.getAllExpenseGroups(),
            transactionRepository.getAllTransactions(),
            settlementRepository.getAllSettlements(),
            friendRepository.getAllFriends()
        ) { groups, txns, settlements, friends ->
            CoreDataBundle(groups, txns, settlements, friends)
        }

        val metaFlow = combine(
            categoryRepository.getAllCategories(),
            groupActivityRepository.getAllActivities(),
            userPreferencesRepository.userPreferencesFlow
        ) { cats, activities, prefs ->
            MetaDataBundle(cats, activities, prefs)
        }

        viewModelScope.launch {
            combine(coreFlow, metaFlow) { core, meta ->
                computeState(
                    groups = core.groups,
                    txns = core.txns,
                    settlements = core.settlements,
                    friends = core.friends,
                    cats = meta.cats,
                    activities = meta.activities,
                    prefs = meta.prefs
                )
            }
            .flowOn(Dispatchers.Default)
            .catch { _ ->
                _uiState.update { it.copy(isLoading = false) }
            }
            .collect { newState ->
                _uiState.value = newState
            }
        }
    }

    private fun computeState(
        groups: List<ExpenseGroup>,
        txns: List<Transaction>,
        settlements: List<Settlement>,
        friends: List<Friend>,
        cats: List<Category>,
        activities: List<GroupActivity>,
        prefs: UserPreferences
    ): SplitProUiState {
        val current = _uiState.value
        val summaries = mutableMapOf<Long, GroupBalanceSummary>()

        var totalExpenses = 0L
        var totalOthersOweMe = 0L
        var totalIOweOthers = 0L
        var upcomingTransfers = 0

        for (g in groups) {
            val groupTxns = txns.filter { it.expenseGroupId == g.id }
            val groupSettlements = settlements.filter { it.groupId == g.id }
            val groupMembers = friends.filter { g.memberFriendIds.contains(it.id) }

            val summary = LedgerCalculator.computeGroupBalances(
                groupId = g.id,
                transactions = groupTxns,
                settlements = groupSettlements,
                members = groupMembers,
                allFriends = friends
            )
            summaries[g.id] = summary

            if (!g.isArchived) {
                totalExpenses = SafeMath.safeAdd(totalExpenses, summary.totalExpensePaise)
                if (summary.myNetBalancePaise > 0L) {
                    totalOthersOweMe = SafeMath.safeAdd(totalOthersOweMe, summary.myNetBalancePaise)
                } else if (summary.myNetBalancePaise < 0L) {
                    totalIOweOthers = SafeMath.safeAdd(totalIOweOthers, SafeMath.safeAbs(summary.myNetBalancePaise))
                }
                upcomingTransfers += summary.simplifiedDebts.size
            }
        }

        val filtered = filterGroups(
            groups = groups,
            type = current.selectedGroupType,
            showArchived = current.showArchived,
            query = current.searchQuery
        )

        return current.copy(
            isLoading = false,
            groups = groups,
            filteredGroups = filtered,
            friends = friends,
            categories = cats,
            allTransactions = txns,
            allSettlements = settlements,
            recentActivities = activities.take(15),
            userPreferences = prefs,
            totalGroupsCount = groups.count { !it.isArchived },
            totalGroupExpensesPaise = totalExpenses,
            totalOthersOweMePaise = totalOthersOweMe,
            totalIOweOthersPaise = totalIOweOthers,
            upcomingSettlementsCount = upcomingTransfers,
            groupSummaries = summaries
        )
    }

    private fun filterGroups(
        groups: List<ExpenseGroup>,
        type: GroupType?,
        showArchived: Boolean,
        query: String
    ): List<ExpenseGroup> {
        return groups.filter { g ->
            val matchArchive = if (showArchived) true else !g.isArchived
            val matchType = if (type == null) true else g.groupType == type
            val matchQuery = if (query.isBlank()) true else {
                g.name.contains(query, ignoreCase = true) ||
                        (g.description ?: "").contains(query, ignoreCase = true) ||
                        (g.destination ?: "").contains(query, ignoreCase = true)
            }
            matchArchive && matchType && matchQuery
        }
    }

    fun setFilterGroupType(type: GroupType?) {
        _uiState.update { current ->
            current.copy(
                selectedGroupType = type,
                filteredGroups = filterGroups(current.groups, type, current.showArchived, current.searchQuery)
            )
        }
    }

    fun setShowArchived(show: Boolean) {
        _uiState.update { current ->
            current.copy(
                showArchived = show,
                filteredGroups = filterGroups(current.groups, current.selectedGroupType, show, current.searchQuery)
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredGroups = filterGroups(current.groups, current.selectedGroupType, current.showArchived, query)
            )
        }
    }

    fun createGroup(
        name: String,
        type: GroupType,
        description: String? = null,
        destination: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        coverImageUri: String? = null,
        colorHex: String? = null,
        friendIds: List<Long> = emptyList(),
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val newId = expenseGroupRepository.createExpenseGroup(
                    name = name,
                    description = description,
                    destination = destination,
                    startDate = startDate,
                    endDate = endDate,
                    coverImageUri = coverImageUri,
                    iconName = when (type) {
                        GroupType.TRIP -> "flight"
                        GroupType.HOME -> "home"
                        GroupType.FRIENDS -> "group"
                        GroupType.COUPLE -> "favorite"
                        GroupType.FAMILY -> "family_restroom"
                        GroupType.OFFICE -> "work"
                        GroupType.CUSTOM -> "dashboard"
                    },
                    colorHex = colorHex ?: "#3B82F6",
                    groupType = type,
                    currencyCode = _uiState.value.userPreferences.splitProCurrency,
                    simplifyDebts = _uiState.value.userPreferences.debtSimplificationEnabled,
                    friendIds = friendIds
                )
                try {
                    groupActivityRepository.recordActivity(
                        groupId = newId,
                        activityType = GroupActivityType.GROUP_CREATED,
                        actorName = "You",
                        description = "Created group \"$name\""
                    )
                } catch (_: Exception) { }
                onCreated(newId)
            } catch (_: Exception) { }
        }
    }

    fun updateGroup(
        group: ExpenseGroup,
        friendIds: List<Long>? = null,
        onUpdated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                expenseGroupRepository.updateExpenseGroup(group, friendIds)
                try {
                    groupActivityRepository.recordActivity(
                        groupId = group.id,
                        activityType = GroupActivityType.GROUP_UPDATED,
                        actorName = "You",
                        description = "Updated group details for \"${group.name}\""
                    )
                } catch (_: Exception) { }
                onUpdated()
            } catch (_: Exception) { }
        }
    }

    fun toggleArchive(group: ExpenseGroup) {
        viewModelScope.launch {
            try {
                val willArchive = !group.isArchived
                expenseGroupRepository.setArchived(group.id, willArchive)
                try {
                    groupActivityRepository.recordActivity(
                        groupId = group.id,
                        activityType = GroupActivityType.GROUP_ARCHIVED,
                        actorName = "You",
                        description = if (willArchive) "Archived group \"${group.name}\"" else "Restored group \"${group.name}\""
                    )
                } catch (_: Exception) { }
            } catch (_: Exception) { }
        }
    }

    fun deleteGroup(groupId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                expenseGroupRepository.deleteExpenseGroup(groupId)
                onDeleted()
            } catch (_: Exception) { }
        }
    }

    fun saveGroupExpense(
        transaction: Transaction,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val isNew = transaction.id <= 0L
                val savedId = transactionRepository.saveTransaction(transaction)
                val gId = transaction.expenseGroupId
                if (gId != null && gId > 0L) {
                    val actType = if (isNew) GroupActivityType.EXPENSE_CREATED else GroupActivityType.EXPENSE_EDITED
                    val desc = if (isNew) "Added expense \"${transaction.title}\"" else "Edited expense \"${transaction.title}\""
                    try {
                        groupActivityRepository.recordActivity(
                            groupId = gId,
                            activityType = actType,
                            actorName = transaction.paidByName ?: "You",
                            description = desc,
                            amountPaise = transaction.amountPaise,
                            targetId = savedId
                        )
                    } catch (_: Exception) { }
                }
                onSaved()
            } catch (_: Exception) { }
        }
    }

    fun deleteGroupExpense(transaction: Transaction) {
        viewModelScope.launch {
            try {
                transactionRepository.deleteTransaction(transaction.id)
                val gId = transaction.expenseGroupId
                if (gId != null && gId > 0L) {
                    try {
                        groupActivityRepository.recordActivity(
                            groupId = gId,
                            activityType = GroupActivityType.EXPENSE_DELETED,
                            actorName = "You",
                            description = "Deleted expense \"${transaction.title}\"",
                            amountPaise = transaction.amountPaise,
                            targetId = transaction.id
                        )
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
        }
    }

    fun recordGroupSettlement(
        settlement: Settlement,
        onRecorded: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val id = settlementRepository.recordSettlement(settlement)
                val gId = settlement.groupId
                if (gId != null && gId > 0L) {
                    val payerName = if (settlement.payerId == null) "You" else _uiState.value.friends.firstOrNull { it.id == settlement.payerId }?.name ?: "Unavailable Member"
                    val receiverName = if (settlement.receiverId == null) "You" else _uiState.value.friends.firstOrNull { it.id == settlement.receiverId }?.name ?: "Unavailable Member"
                    try {
                        groupActivityRepository.recordActivity(
                            groupId = gId,
                            activityType = GroupActivityType.SETTLEMENT_RECORDED,
                            actorName = payerName,
                            description = "$payerName paid $receiverName",
                            amountPaise = settlement.amountPaise,
                            targetId = id
                        )
                    } catch (_: Exception) { }
                }
                onRecorded(id)
            } catch (_: Exception) { }
        }
    }

    suspend fun getTransactionById(id: Long): Transaction? {
        return transactionRepository.getTransactionById(id)
    }

    fun reverseGroupSettlement(
        settlement: Settlement,
        onReversed: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val success = settlementRepository.reverseSettlement(settlement.id)
                val gId = settlement.groupId
                if (success && gId != null && gId > 0L) {
                    val payerName = if (settlement.payerId == null) "You" else _uiState.value.friends.firstOrNull { it.id == settlement.payerId }?.name ?: "Unavailable Member"
                    val receiverName = if (settlement.receiverId == null) "You" else _uiState.value.friends.firstOrNull { it.id == settlement.receiverId }?.name ?: "Unavailable Member"
                    try {
                        groupActivityRepository.recordActivity(
                            groupId = gId,
                            activityType = GroupActivityType.SETTLEMENT_REVERSED,
                            actorName = "You",
                            description = "Reversed payment: $payerName paid $receiverName (₹${settlement.amountPaise / 100})",
                            amountPaise = settlement.amountPaise,
                            targetId = settlement.id
                        )
                    } catch (_: Exception) { }
                }
                onReversed(success)
            } catch (_: Exception) {
                onReversed(false)
            }
        }
    }

    fun restoreGroupSettlement(
        settlement: Settlement,
        onRestored: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val success = settlementRepository.unreverseSettlement(settlement.id)
                val gId = settlement.groupId
                if (success && gId != null && gId > 0L) {
                    val payerName = if (settlement.payerId == null) "You" else _uiState.value.friends.firstOrNull { it.id == settlement.payerId }?.name ?: "Unavailable Member"
                    val receiverName = if (settlement.receiverId == null) "You" else _uiState.value.friends.firstOrNull { it.id == settlement.receiverId }?.name ?: "Unavailable Member"
                    try {
                        groupActivityRepository.recordActivity(
                            groupId = gId,
                            activityType = GroupActivityType.SETTLEMENT_RESTORED,
                            actorName = "You",
                            description = "Restored payment: $payerName paid $receiverName (₹${settlement.amountPaise / 100})",
                            amountPaise = settlement.amountPaise,
                            targetId = settlement.id
                        )
                    } catch (_: Exception) { }
                }
                onRestored(success)
            } catch (_: Exception) {
                onRestored(false)
            }
        }
    }

    fun quickAddFriend(
        name: String,
        phone: String? = null,
        email: String? = null,
        onAdded: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val newFriend = Friend(
                    name = name.trim(),
                    phoneNumber = phone?.trim()?.ifBlank { null },
                    email = email?.trim()?.ifBlank { null },
                    createdAt = System.currentTimeMillis()
                )
                val id = friendRepository.addFriend(newFriend)
                onAdded(id)
            } catch (_: Exception) { }
        }
    }

    fun getGroupFlow(groupId: Long): Flow<ExpenseGroup?> {
        return expenseGroupRepository.getExpenseGroupByIdFlow(groupId)
    }

    fun getGroupActivities(groupId: Long): Flow<List<GroupActivity>> {
        return groupActivityRepository.getActivitiesForGroup(groupId)
    }

    fun getGroupSettlements(groupId: Long): Flow<List<Settlement>> {
        return settlementRepository.getSettlementsForGroup(groupId)
    }

    val upiSettlementManager = com.naman.rupeeflow.upi.UpiSettlementManager(
        settlementRepository = settlementRepository,
        groupActivityRepository = groupActivityRepository
    )

    fun updateFriend(friend: Friend) {
        viewModelScope.launch {
            try {
                friendRepository.updateFriend(friend)
            } catch (_: Exception) { }
        }
    }
}

