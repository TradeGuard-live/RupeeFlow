package com.naman.rupeeflow.ui.screens.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.safeSumOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

private data class OverviewBundle(
    val transactions: List<Transaction>,
    val friends: List<Friend>,
    val settlements: List<Settlement>,
    val pendingList: List<PendingTransaction>,
    val categories: List<Category>
)

data class OverviewUiState(
    val isLoading: Boolean = true,
    val summary: FinancialSummary = FinancialSummary(),
    val weeklySpending: List<DailySpending> = emptyList(),
    val weekRangeLabel: String = "",
    val weekOffset: Int = 0,
    val recentTransactions: List<Transaction> = emptyList(),
    val friendsWithBalances: List<Friend> = emptyList(),
    val pendingTransactions: List<PendingTransaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val allFriends: List<Friend> = emptyList()
)

class OverviewViewModel(
    private val transactionRepository: TransactionRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository,
    private val pendingTransactionRepository: PendingTransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    private val weekOffset = MutableStateFlow(0)

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val baseFlow = combine(
                transactionRepository.getAllTransactions(),
                friendRepository.getActiveFriends(),
                settlementRepository.getAllSettlements(),
                pendingTransactionRepository.getPendingTransactions(),
                categoryRepository.getAllCategories()
            ) { transactions, friends, settlements, pendingList, categories ->
                OverviewBundle(transactions, friends, settlements, pendingList, categories)
            }

            combine(baseFlow, weekOffset) { bundle, offset ->
                val friendsWithLedger = LedgerCalculator.computeFriendLedgers(bundle.friends, bundle.transactions, bundle.settlements)

                val startOfMonth = DateTimeUtils.getStartOfMonth()
                val summary = LedgerCalculator.computeFinancialSummary(
                    transactions = bundle.transactions,
                    friends = friendsWithLedger,
                    pendingCount = bundle.pendingList.size,
                    startOfMonth = startOfMonth
                )

                // Weekly spending calculation starting strictly from Monday
                val (weeklySpending, weekLabel) = calculateWeeklySpending(bundle.transactions, offset)

                // Recent active transactions
                val recent = bundle.transactions
                    .filter { it.transactionStatus == TransactionStatus.ACTIVE }
                    .take(6)

                // Friends with outstanding non-zero balance
                val outstandingFriends = friendsWithLedger
                    .filter { it.netBalancePaise != 0L }
                    .sortedByDescending { kotlin.math.abs(it.netBalancePaise) }

                OverviewUiState(
                    isLoading = false,
                    summary = summary,
                    weeklySpending = weeklySpending,
                    weekRangeLabel = weekLabel,
                    weekOffset = offset,
                    recentTransactions = recent,
                    friendsWithBalances = outstandingFriends,
                    pendingTransactions = bundle.pendingList,
                    categories = bundle.categories,
                    allFriends = bundle.friends
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    private fun calculateWeeklySpending(transactions: List<Transaction>, offset: Int): Pair<List<DailySpending>, String> {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday + (offset * 7))
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val mondayStart = cal.timeInMillis

        val sundayCal = Calendar.getInstance().apply {
            timeInMillis = mondayStart
            add(Calendar.DAY_OF_YEAR, 6)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val sundayEnd = sundayCal.timeInMillis

        val dateFormat = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
        val rangeLabel = if (offset == 0) {
            "This Week (${dateFormat.format(java.util.Date(mondayStart))} - ${dateFormat.format(java.util.Date(sundayEnd))})"
        } else if (offset == -1) {
            "Last Week (${dateFormat.format(java.util.Date(mondayStart))} - ${dateFormat.format(java.util.Date(sundayEnd))})"
        } else {
            "${dateFormat.format(java.util.Date(mondayStart))} - ${dateFormat.format(java.util.Date(sundayEnd))}"
        }

        val days = mutableListOf<DailySpending>()
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        for (i in 0..6) {
            val dCal = Calendar.getInstance().apply {
                timeInMillis = mondayStart
                add(Calendar.DAY_OF_YEAR, i)
            }
            val startOfDay = dCal.timeInMillis
            val endOfDay = startOfDay + (24 * 60 * 60 * 1000L) - 1

            val dayTotal = transactions
                .filter {
                    it.transactionStatus == TransactionStatus.ACTIVE &&
                            it.transactionType == TransactionType.EXPENSE &&
                            it.transactionDate in startOfDay..endOfDay
                }
                .safeSumOf { it.userExpensePaise }

            days.add(DailySpending(startOfDay, dayNames[i], dayTotal))
        }

        return Pair(days, rangeLabel)
    }

    fun previousWeek() {
        weekOffset.update { it - 1 }
    }

    fun nextWeek() {
        weekOffset.update { it + 1 }
    }

    fun currentWeek() {
        weekOffset.value = 0
    }

    fun saveTransaction(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.saveTransaction(transaction)
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

    fun recordSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementRepository.recordSettlement(settlement)
        }
    }

    fun resolvePending(id: Long) {
        viewModelScope.launch {
            pendingTransactionRepository.resolvePendingTransaction(id)
        }
    }

    fun ignorePending(id: Long) {
        viewModelScope.launch {
            pendingTransactionRepository.ignorePendingTransaction(id)
        }
    }
}
