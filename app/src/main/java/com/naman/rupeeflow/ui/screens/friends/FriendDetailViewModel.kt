package com.naman.rupeeflow.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FriendDetailUiState(
    val isLoading: Boolean = true,
    val friend: Friend? = null,
    val relatedTransactions: List<Transaction> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    val totalSharedExpensesPaise: Long = 0L,
    val totalPaymentsReceivedPaise: Long = 0L,
    val totalPaymentsMadePaise: Long = 0L,
    val remainingBalancePaise: Long = 0L,
    val filterType: String = "ALL", // "ALL", "EXPENSES", "SETTLEMENTS"
    val allFriends: List<Friend> = emptyList(),
    val categories: List<Category> = emptyList()
)

class FriendDetailViewModel(
    private val friendId: Long,
    private val friendRepository: FriendRepository,
    private val transactionRepository: TransactionRepository,
    private val settlementRepository: SettlementRepository,
    private val categoryRepository: CategoryRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendDetailUiState())
    val uiState: StateFlow<FriendDetailUiState> = _uiState.asStateFlow()

    init {
        loadFriendDetails()
    }

    private fun loadFriendDetails() {
        viewModelScope.launch {
            val categoriesFlow = categoryRepository?.getAllCategories() ?: flowOf(emptyList())
            combine(
                friendRepository.getFriendByIdFlow(friendId),
                transactionRepository.getAllTransactions(),
                settlementRepository.getSettlementsForFriend(friendId),
                friendRepository.getAllFriends(),
                categoriesFlow
            ) { rawFriend, allTxns, settlements, allFriends, categories ->
                if (rawFriend == null) return@combine null

                // Compute ledger for this friend
                val friendsWithLedger = LedgerCalculator.computeFriendLedgers(listOf(rawFriend), allTxns, settlements)
                val computedFriend = friendsWithLedger.firstOrNull() ?: rawFriend

                // Filter related transactions where friend is participant OR payer
                val relatedTxns = allTxns.filter { txn ->
                    txn.paidByFriendId == friendId ||
                            txn.participants.any { it.friendId == friendId }
                }

                // Shared expenses sum
                val totalShared = relatedTxns
                    .filter { it.transactionStatus == TransactionStatus.ACTIVE && it.transactionType == TransactionType.EXPENSE }
                    .sumOf { txn ->
                        val friendPart = txn.participants.firstOrNull { it.friendId == friendId }
                        friendPart?.shareAmountPaise ?: 0L
                    }

                // Settlements sum
                val validSettlements = settlements.filter { !it.isReversed }
                val received = validSettlements
                    .filter { it.payerId == friendId && it.receiverId == null }
                    .sumOf { it.amountPaise }
                val paid = validSettlements
                    .filter { it.payerId == null && it.receiverId == friendId }
                    .sumOf { it.amountPaise }

                FriendDetailUiState(
                    isLoading = false,
                    friend = computedFriend,
                    relatedTransactions = relatedTxns,
                    settlements = settlements,
                    totalSharedExpensesPaise = totalShared,
                    totalPaymentsReceivedPaise = received,
                    totalPaymentsMadePaise = paid,
                    remainingBalancePaise = computedFriend.netBalancePaise,
                    filterType = _uiState.value.filterType,
                    allFriends = allFriends,
                    categories = categories
                )
            }.collect { state ->
                if (state != null) {
                    _uiState.value = state
                }
            }
        }
    }

    fun setFilterType(filter: String) {
        _uiState.update { it.copy(filterType = filter) }
    }

    fun recordSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementRepository.recordSettlement(settlement)
        }
    }

    // ✅ Update existing settlement with ledger recalculation
    fun updateSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementRepository.updateSettlement(settlement)
        }
    }

    fun reverseSettlement(settlementId: Long) {
        viewModelScope.launch {
            settlementRepository.reverseSettlement(settlementId)
        }
    }

    fun unreverseSettlement(settlementId: Long) {
        viewModelScope.launch {
            settlementRepository.unreverseSettlement(settlementId)
        }
    }

    fun deleteSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementRepository.deleteSettlement(settlement)
        }
    }

    // ✅ Transaction operations
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

    // ✅ Safe friend archiving & deletion
    fun archiveFriend() {
        viewModelScope.launch {
            friendRepository.archiveFriend(friendId)
        }
    }

    fun unarchiveFriend() {
        viewModelScope.launch {
            friendRepository.unarchiveFriend(friendId)
        }
    }

    fun deleteFriend(onComplete: () -> Unit) {
        viewModelScope.launch {
            val f = _uiState.value.friend ?: return@launch
            friendRepository.deleteFriend(f)
            onComplete()
        }
    }
}
