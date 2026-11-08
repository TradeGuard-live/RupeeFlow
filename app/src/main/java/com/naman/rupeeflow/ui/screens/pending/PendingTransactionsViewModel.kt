package com.naman.rupeeflow.ui.screens.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.FriendGroupRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.PendingTransactionRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PendingUiState(
    val isLoading: Boolean = true,
    val allDetectedTransactions: List<PendingTransaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val friends: List<Friend> = emptyList(),
    val friendGroups: List<FriendGroup> = emptyList()
) {
    val pendingTransactions: List<PendingTransaction>
        get() = allDetectedTransactions.filter { it.status == PendingStatus.PENDING }

    val addedTransactions: List<PendingTransaction>
        get() = allDetectedTransactions.filter { it.status == PendingStatus.RESOLVED }

    val ignoredTransactions: List<PendingTransaction>
        get() = allDetectedTransactions.filter { it.status == PendingStatus.IGNORED }
}

class PendingTransactionsViewModel(
    private val pendingTransactionRepository: PendingTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository,
    private val friendGroupRepository: FriendGroupRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PendingUiState())
    val uiState: StateFlow<PendingUiState> = _uiState.asStateFlow()

    init {
        loadPending()
    }

    private fun loadPending() {
        viewModelScope.launch {
            val groupsFlow = friendGroupRepository?.getAllGroups() ?: flowOf(emptyList())
            combine(
                pendingTransactionRepository.getAllDetectedTransactions(),
                categoryRepository.getAllCategories(),
                friendRepository.getActiveFriends(),
                groupsFlow
            ) { detectedList, categories, friends, groups ->
                PendingUiState(
                    isLoading = false,
                    allDetectedTransactions = detectedList,
                    categories = categories,
                    friends = friends,
                    friendGroups = groups
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun saveTransaction(txn: Transaction, pendingId: Long? = null) {
        viewModelScope.launch {
            transactionRepository.saveTransaction(txn)
            if (pendingId != null) {
                pendingTransactionRepository.resolvePendingTransaction(pendingId)
            }
        }
    }

    fun saveSettlement(settlement: Settlement, pendingId: Long? = null) {
        viewModelScope.launch {
            settlementRepository.recordSettlement(settlement)
            if (pendingId != null) {
                pendingTransactionRepository.resolvePendingTransaction(pendingId)
            }
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

    fun restorePending(id: Long) {
        viewModelScope.launch {
            pendingTransactionRepository.restorePendingTransaction(id)
        }
    }

    fun deletePending(id: Long) {
        viewModelScope.launch {
            pendingTransactionRepository.deletePendingTransaction(id)
        }
    }
}
