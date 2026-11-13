package com.naman.rupeeflow.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.ExpenseGroupRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.model.Transaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExpenseGroupsUiState(
    val isLoading: Boolean = true,
    val activeGroups: List<ExpenseGroup> = emptyList(),
    val archivedGroups: List<ExpenseGroup> = emptyList(),
    val selectedTab: Int = 0, // 0 = Active, 1 = Archived
    val searchQuery: String = ""
)

class ExpenseGroupViewModel(
    private val expenseGroupRepository: ExpenseGroupRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpenseGroupsUiState())
    val uiState: StateFlow<ExpenseGroupsUiState> = _uiState.asStateFlow()

    init {
        loadGroups()
    }

    private fun loadGroups() {
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
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun createGroup(name: String, description: String? = null, colorHex: String? = null) {
        viewModelScope.launch {
            expenseGroupRepository.createExpenseGroup(
                name = name,
                description = description,
                iconName = "flight",
                colorHex = colorHex ?: "#3B82F6"
            )
        }
    }

    fun updateGroup(group: ExpenseGroup) {
        viewModelScope.launch {
            expenseGroupRepository.updateExpenseGroup(group)
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
}

