package com.naman.rupeeflow.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val filterType: String = "ALL" // "ALL", "EXPENSES", "SETTLEMENTS"
)

class FriendDetailViewModel(
    private val friendId: Long,
    private val friendRepository: FriendRepository,
    private val transactionRepository: TransactionRepository,
    private val settlementRepository: SettlementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendDetailUiState())
    val uiState: StateFlow<FriendDetailUiState> = _uiState.asStateFlow()

    init {
        loadFriendDetails()
    }

    private fun loadFriendDetails() {
        viewModelScope.launch {
            combine(
                friendRepository.getFriendByIdFlow(friendId),
                transactionRepository.getAllTransactions(),
                settlementRepository.getSettlementsForFriend(friendId)
            ) { rawFriend, allTxns, settlements ->
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
                    .sumOf { it.amountPaise }

                // Total settlements where friend paid ME
                val paymentsReceived = settlements
                    .filter { it.payerId == friendId && !it.isReversed }
                    .sumOf { it.amountPaise }

                // Total settlements where ME paid friend
                val paymentsMade = settlements
                    .filter { it.receiverId == friendId && !it.isReversed }
                    .sumOf { it.amountPaise }

                FriendDetailUiState(
                    isLoading = false,
                    friend = computedFriend,
                    relatedTransactions = relatedTxns,
                    settlements = settlements,
                    totalSharedExpensesPaise = totalShared,
                    totalPaymentsReceivedPaise = paymentsReceived,
                    totalPaymentsMadePaise = paymentsMade,
                    remainingBalancePaise = computedFriend.netBalancePaise
                )
            }.collect { state ->
                if (state != null) {
                    _uiState.update { current ->
                        state.copy(filterType = current.filterType)
                    }
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
