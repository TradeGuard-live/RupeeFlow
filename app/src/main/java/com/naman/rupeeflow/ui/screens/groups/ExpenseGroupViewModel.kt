package com.naman.rupeeflow.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.ExpenseGroupRepository
import com.naman.rupeeflow.data.repository.FriendGroupRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.model.Category
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendGroup
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.model.Transaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExpenseGroupsUiState(
    val isLoading: Boolean = true,
    val activeGroups: List<ExpenseGroup> = emptyList(),
    val archivedGroups: List<ExpenseGroup> = emptyList(),
    val selectedTab: Int = 0, // 0 = Active, 1 = Archived
    val searchQuery: String = "",
    val friends: List<Friend> = emptyList(),
    val friendGroups: List<FriendGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val allTransactions: List<Transaction> = emptyList(),
    val allSettlements: List<Settlement> = emptyList()
)

class ExpenseGroupViewModel(
    private val expenseGroupRepository: ExpenseGroupRepository,
    private val transactionRepository: TransactionRepository,
    private val friendRepository: FriendRepository? = null,
    private val friendGroupRepository: FriendGroupRepository? = null,
    private val settlementRepository: SettlementRepository? = null,
    private val categoryRepository: CategoryRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseGroupsUiState())
    val uiState: StateFlow<ExpenseGroupsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            expenseGroupRepository.getAllExpenseGroups().collect { allGroups ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        activeGroups = allGroups.filter { !it.isArchived },
                        archivedGroups = allGroups.filter { it.isArchived }
                    )
                }
            }
        }

        if (friendRepository != null) {
            viewModelScope.launch {
                friendRepository.getAllFriends().collect { friends ->
                    _uiState.update { it.copy(friends = friends) }
                }
            }
        }

        if (friendGroupRepository != null) {
            viewModelScope.launch {
                friendGroupRepository.getAllGroups().collect { groups ->
                    _uiState.update { it.copy(friendGroups = groups) }
                }
            }
        }

        if (categoryRepository != null) {
            viewModelScope.launch {
                categoryRepository.getAllCategories().collect { cats ->
                    _uiState.update { it.copy(categories = cats) }
                }
            }
        }

        viewModelScope.launch {
            transactionRepository.getAllTransactions().collect { txns ->
                _uiState.update { it.copy(allTransactions = txns) }
            }
        }

        if (settlementRepository != null) {
            viewModelScope.launch {
                settlementRepository.getAllSettlements().collect { settlements ->
                    _uiState.update { it.copy(allSettlements = settlements) }
                }
            }
        }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun createGroup(name: String, description: String? = null, colorHex: String? = null) {
        createTrip(
            name = name,
            description = description,
            colorHex = colorHex
        )
    }

    fun createTrip(
        name: String,
        destination: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        description: String? = null,
        coverImageUri: String? = null,
        colorHex: String? = null,
        friendIds: List<Long> = emptyList(),
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val newId = expenseGroupRepository.createExpenseGroup(
                name = name,
                description = description,
                destination = destination,
                startDate = startDate,
                endDate = endDate,
                coverImageUri = coverImageUri,
                iconName = "flight",
                colorHex = colorHex ?: "#3B82F6",
                friendIds = friendIds
            )
            onCreated(newId)
        }
    }

    fun updateGroup(group: ExpenseGroup) {
        viewModelScope.launch {
            expenseGroupRepository.updateExpenseGroup(group)
        }
    }

    fun updateTrip(
        group: ExpenseGroup,
        friendIds: List<Long>? = null,
        onUpdated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            expenseGroupRepository.updateExpenseGroup(group, friendIds)
            onUpdated()
        }
    }

    fun quickAddFriend(
        name: String,
        phone: String? = null,
        email: String? = null,
        onAdded: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            if (friendRepository != null) {
                val newFriend = Friend(
                    name = name.trim(),
                    phoneNumber = phone?.trim()?.ifBlank { null },
                    email = email?.trim()?.ifBlank { null },
                    createdAt = System.currentTimeMillis()
                )
                val newId = friendRepository.addFriend(newFriend)
                onAdded(newId)
            }
        }
    }

    fun toggleArchive(group: ExpenseGroup) {
        viewModelScope.launch {
            expenseGroupRepository.setArchived(group.id, !group.isArchived)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            expenseGroupRepository.deleteExpenseGroup(groupId)
        }
    }

    fun getTransactionsForGroup(groupId: Long): Flow<List<Transaction>> {
        return transactionRepository.getTransactionsForExpenseGroup(groupId)
    }

    fun getGroupByIdFlow(groupId: Long): Flow<ExpenseGroup?> {
        return expenseGroupRepository.getExpenseGroupByIdFlow(groupId)
    }

    fun recreateLinkedFriendGroup(groupId: Long, friendIds: List<Long>) {
        viewModelScope.launch {
            expenseGroupRepository.recreateLinkedFriendGroup(groupId, friendIds)
        }
    }

    fun saveTransaction(transaction: Transaction, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            transactionRepository.saveTransaction(transaction)
            onSaved()
        }
    }
}
