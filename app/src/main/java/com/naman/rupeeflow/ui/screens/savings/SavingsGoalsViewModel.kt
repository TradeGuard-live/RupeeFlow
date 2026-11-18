package com.naman.rupeeflow.ui.screens.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.SavingsGoalRepository
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SavingsGoalsUiState(
    val isLoading: Boolean = true,
    val goals: List<SavingsGoal> = emptyList(),
    val selectedStatusTab: SavingsGoalStatus = SavingsGoalStatus.ACTIVE,
    val overviewSummary: SavingsOverviewSummary = SavingsOverviewSummary(),
    val recentEntries: List<SavingsEntry> = emptyList(),
    val errorMessage: String? = null
) {
    val activeGoals: List<SavingsGoal> get() = goals.filter { it.status == SavingsGoalStatus.ACTIVE }
    val completedGoals: List<SavingsGoal> get() = goals.filter { it.status == SavingsGoalStatus.COMPLETED }
    val pausedGoals: List<SavingsGoal> get() = goals.filter { it.status == SavingsGoalStatus.PAUSED }
    val archivedGoals: List<SavingsGoal> get() = goals.filter { it.status == SavingsGoalStatus.ARCHIVED }

    val displayedGoals: List<SavingsGoal> get() = when (selectedStatusTab) {
        SavingsGoalStatus.ACTIVE -> activeGoals
        SavingsGoalStatus.COMPLETED -> completedGoals
        SavingsGoalStatus.PAUSED -> pausedGoals
        SavingsGoalStatus.ARCHIVED -> archivedGoals
    }
}

class SavingsGoalsViewModel(
    private val savingsGoalRepository: SavingsGoalRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(SavingsGoalStatus.ACTIVE)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SavingsGoalsUiState> = combine(
        savingsGoalRepository.getAllGoals(),
        savingsGoalRepository.getOverviewSummary(),
        savingsGoalRepository.getAllEntries(),
        _selectedTab,
        _errorMessage
    ) { goals, summary, allEntries, tab, error ->
        SavingsGoalsUiState(
            isLoading = false,
            goals = goals,
            selectedStatusTab = tab,
            overviewSummary = summary,
            recentEntries = allEntries.take(10),
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavingsGoalsUiState()
    )

    fun selectTab(tab: SavingsGoalStatus) {
        _selectedTab.value = tab
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun createGoal(
        name: String,
        targetAmountPaise: Long,
        initialSavedAmountPaise: Long = 0L,
        targetDate: Long? = null,
        category: SavingsGoalCategory = SavingsGoalCategory.CUSTOM,
        description: String? = null,
        imageUri: String? = null,
        colorHex: String = "#FFAD91",
        priority: SavingsGoalPriority = SavingsGoalPriority.MEDIUM,
        onSuccess: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val goalId = savingsGoalRepository.createGoal(
                    name = name,
                    targetAmountPaise = targetAmountPaise,
                    initialSavedAmountPaise = initialSavedAmountPaise,
                    targetDate = targetDate,
                    category = category,
                    description = description,
                    imageUri = imageUri,
                    colorHex = colorHex,
                    priority = priority
                )
                onSuccess(goalId)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to create savings goal"
            }
        }
    }

    fun setGoalStatus(goalId: Long, status: SavingsGoalStatus) {
        viewModelScope.launch {
            try {
                savingsGoalRepository.setGoalStatus(goalId, status)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update goal status"
            }
        }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch {
            try {
                savingsGoalRepository.deleteGoal(goalId)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to delete goal"
            }
        }
    }

    fun addDeposit(
        goalId: Long,
        amountPaise: Long,
        entryDate: Long,
        description: String,
        paymentMethod: String? = null,
        note: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                savingsGoalRepository.addDeposit(
                    goalId = goalId,
                    amountPaise = amountPaise,
                    entryDate = entryDate,
                    description = description,
                    paymentMethod = paymentMethod,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to add deposit"
            }
        }
    }
}
