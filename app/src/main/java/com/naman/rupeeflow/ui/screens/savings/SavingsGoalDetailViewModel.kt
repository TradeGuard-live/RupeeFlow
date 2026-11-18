package com.naman.rupeeflow.ui.screens.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.SavingsGoalRepository
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.SafeMath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class MonthlyContribution(
    val monthYearLabel: String,
    val year: Int,
    val month: Int,
    val netAmountPaise: Long,
    val depositAmountPaise: Long,
    val withdrawalAmountPaise: Long
)

data class SavingsGoalDetailUiState(
    val isLoading: Boolean = true,
    val goal: SavingsGoal? = null,
    val entries: List<SavingsEntry> = emptyList(),
    val dailyGroups: List<DailySavingsGroup> = emptyList(),
    val searchQuery: String = "",
    val startDateFilter: Long? = null,
    val endDateFilter: Long? = null,
    val selectedCalendarDate: Long? = null,
    val selectedCalendarEntries: List<SavingsEntry> = emptyList(),
    val selectedCalendarTotalPaise: Long = 0L,
    val dailyTotalPaise: Long = 0L,
    val weeklyTotalPaise: Long = 0L,
    val monthlyTotalPaise: Long = 0L,
    val allTimeTotalPaise: Long = 0L,
    val monthlyContributions: List<MonthlyContribution> = emptyList(),
    val errorMessage: String? = null,
    val isCelebrationVisible: Boolean = false
) {
    val filteredDailyGroups: List<DailySavingsGroup> get() {
        val query = searchQuery.trim().lowercase()
        return dailyGroups.mapNotNull { group ->
            // Filter by date range
            if (startDateFilter != null && group.dateMillis < startDateFilter) return@mapNotNull null
            if (endDateFilter != null && group.dateMillis > endDateFilter) return@mapNotNull null

            // Filter entries by query
            val matchingEntries = if (query.isBlank()) {
                group.entries
            } else {
                group.entries.filter {
                    it.description.lowercase().contains(query) ||
                            (it.paymentMethod?.lowercase()?.contains(query) == true) ||
                            (it.note?.lowercase()?.contains(query) == true)
                }
            }

            if (matchingEntries.isEmpty()) null
            else {
                var dayDeposits = 0L
                var dayWithdrawals = 0L
                for (e in matchingEntries) {
                    if (e.entryType == SavingsEntryType.DEPOSIT) dayDeposits = SafeMath.safeAdd(dayDeposits, e.amountPaise)
                    else dayWithdrawals = SafeMath.safeAdd(dayWithdrawals, e.amountPaise)
                }
                group.copy(
                    totalSavedPaise = SafeMath.safeSubtract(dayDeposits, dayWithdrawals),
                    totalDepositsPaise = dayDeposits,
                    totalWithdrawalsPaise = dayWithdrawals,
                    entries = matchingEntries
                )
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SavingsGoalDetailViewModel(
    private val savingsGoalRepository: SavingsGoalRepository
) : ViewModel() {

    private val _goalId = MutableStateFlow<Long?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _startDateFilter = MutableStateFlow<Long?>(null)
    private val _endDateFilter = MutableStateFlow<Long?>(null)
    private val _selectedCalendarDate = MutableStateFlow<Long?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isCelebrationDismissed = MutableStateFlow(false)

    val uiState: StateFlow<SavingsGoalDetailUiState> = _goalId
        .filterNotNull()
        .flatMapLatest { id ->
            combine(
                savingsGoalRepository.getGoalById(id),
                savingsGoalRepository.getEntriesForGoal(id),
                savingsGoalRepository.getDailySavingsGroups(id),
                _searchQuery,
                _startDateFilter,
                _endDateFilter,
                _selectedCalendarDate,
                _errorMessage,
                _isCelebrationDismissed
            ) { params ->
                val goal = params[0] as SavingsGoal?
                @Suppress("UNCHECKED_CAST")
                val entries = params[1] as List<SavingsEntry>
                @Suppress("UNCHECKED_CAST")
                val dailyGroups = params[2] as List<DailySavingsGroup>
                val query = params[3] as String
                val startFilter = params[4] as Long?
                val endFilter = params[5] as Long?
                val selectedCalDate = params[6] as Long?
                val error = params[7] as String?
                val celebrationDismissed = params[8] as Boolean

                // Calculate totals
                val now = System.currentTimeMillis()
                val todayStart = normalizeToStartOfDay(now)
                val cal = Calendar.getInstance().apply { timeInMillis = now }

                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val weekStart = normalizeToStartOfDay(cal.timeInMillis)

                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val monthStart = normalizeToStartOfDay(cal.timeInMillis)

                var dailyTotal = 0L
                var weeklyTotal = 0L
                var monthlyTotal = 0L
                var allTimeTotal = 0L

                for (e in entries) {
                    val amt = if (e.entryType == SavingsEntryType.DEPOSIT) e.amountPaise else -e.amountPaise
                    allTimeTotal = SafeMath.safeAdd(allTimeTotal, amt)

                    if (e.entryDate >= monthStart) {
                        monthlyTotal = SafeMath.safeAdd(monthlyTotal, amt)
                    }
                    if (e.entryDate >= weekStart) {
                        weeklyTotal = SafeMath.safeAdd(weeklyTotal, amt)
                    }
                    if (e.entryDate >= todayStart) {
                        dailyTotal = SafeMath.safeAdd(dailyTotal, amt)
                    }
                }

                // Selected calendar day entries
                val selectedDayGroup = selectedCalDate?.let { date ->
                    val norm = normalizeToStartOfDay(date)
                    dailyGroups.find { it.dateMillis == norm }
                }

                // Monthly contributions grouping
                val monthlyMap = LinkedHashMap<String, MonthlyContribution>()
                val monthFormat = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                for (e in entries) {
                    cal.timeInMillis = e.entryDate
                    val key = monthFormat.format(cal.time)
                    val existing = monthlyMap[key] ?: MonthlyContribution(
                        monthYearLabel = key,
                        year = cal.get(Calendar.YEAR),
                        month = cal.get(Calendar.MONTH),
                        netAmountPaise = 0L,
                        depositAmountPaise = 0L,
                        withdrawalAmountPaise = 0L
                    )
                    val newDeposit = if (e.entryType == SavingsEntryType.DEPOSIT) SafeMath.safeAdd(existing.depositAmountPaise, e.amountPaise) else existing.depositAmountPaise
                    val newWithdrawal = if (e.entryType == SavingsEntryType.WITHDRAWAL) SafeMath.safeAdd(existing.withdrawalAmountPaise, e.amountPaise) else existing.withdrawalAmountPaise
                    val newNet = SafeMath.safeSubtract(newDeposit, newWithdrawal)
                    monthlyMap[key] = existing.copy(
                        depositAmountPaise = newDeposit,
                        withdrawalAmountPaise = newWithdrawal,
                        netAmountPaise = newNet
                    )
                }

                val showCelebration = (goal?.isCompleted == true) && !celebrationDismissed

                SavingsGoalDetailUiState(
                    isLoading = false,
                    goal = goal,
                    entries = entries,
                    dailyGroups = dailyGroups,
                    searchQuery = query,
                    startDateFilter = startFilter,
                    endDateFilter = endFilter,
                    selectedCalendarDate = selectedCalDate,
                    selectedCalendarEntries = selectedDayGroup?.entries ?: emptyList(),
                    selectedCalendarTotalPaise = selectedDayGroup?.totalSavedPaise ?: 0L,
                    dailyTotalPaise = dailyTotal,
                    weeklyTotalPaise = weeklyTotal,
                    monthlyTotalPaise = monthlyTotal,
                    allTimeTotalPaise = allTimeTotal,
                    monthlyContributions = monthlyMap.values.toList(),
                    errorMessage = error,
                    isCelebrationVisible = showCelebration
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SavingsGoalDetailUiState()
        )

    fun loadGoal(id: Long) {
        _goalId.value = id
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDateFilter(startDate: Long?, endDate: Long?) {
        _startDateFilter.value = startDate
        _endDateFilter.value = endDate
    }

    fun selectCalendarDate(dateMillis: Long?) {
        _selectedCalendarDate.value = dateMillis
    }

    fun dismissCelebration() {
        _isCelebrationDismissed.value = true
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun addDeposit(
        amountPaise: Long,
        date: Long,
        description: String,
        paymentMethod: String? = null,
        note: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        val id = _goalId.value ?: return
        viewModelScope.launch {
            try {
                savingsGoalRepository.addDeposit(
                    goalId = id,
                    amountPaise = amountPaise,
                    entryDate = date,
                    description = description,
                    paymentMethod = paymentMethod,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to add savings"
            }
        }
    }

    fun withdraw(
        amountPaise: Long,
        date: Long,
        description: String,
        paymentMethod: String? = null,
        note: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        val id = _goalId.value ?: return
        viewModelScope.launch {
            try {
                savingsGoalRepository.withdraw(
                    goalId = id,
                    amountPaise = amountPaise,
                    entryDate = date,
                    description = description,
                    paymentMethod = paymentMethod,
                    note = note
                )
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to withdraw"
            }
        }
    }

    fun updateEntry(entry: SavingsEntry, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                savingsGoalRepository.updateEntry(entry)
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update entry"
            }
        }
    }

    fun deleteEntry(entryId: Long) {
        val id = _goalId.value ?: return
        viewModelScope.launch {
            try {
                savingsGoalRepository.deleteEntry(entryId, id)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to delete entry"
            }
        }
    }

    fun updateGoal(goal: SavingsGoal, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                savingsGoalRepository.updateGoal(goal)
                onSuccess()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update goal"
            }
        }
    }

    fun setGoalStatus(status: SavingsGoalStatus) {
        val id = _goalId.value ?: return
        viewModelScope.launch {
            try {
                savingsGoalRepository.setGoalStatus(id, status)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update goal status"
            }
        }
    }

    fun deleteGoal(onDeleted: () -> Unit) {
        val id = _goalId.value ?: return
        viewModelScope.launch {
            try {
                savingsGoalRepository.deleteGoal(id)
                onDeleted()
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to delete goal"
            }
        }
    }

    private fun normalizeToStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
