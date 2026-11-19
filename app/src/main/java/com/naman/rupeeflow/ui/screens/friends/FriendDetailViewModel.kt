package com.naman.rupeeflow.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.FriendGroupRepository
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
    // ✅ Outstanding / unsettled expenses for this friend
    val unsettledExpenses: List<UnsettledExpenseItem> = emptyList(),
    // Complete expense history (unpaid, partially settled, and fully settled)
    val expenseHistory: List<UnsettledExpenseItem> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    val totalSharedExpensesPaise: Long = 0L,
    val totalPaymentsReceivedPaise: Long = 0L,
    val totalPaymentsMadePaise: Long = 0L,
    val remainingBalancePaise: Long = 0L,
    val filterType: String = "OUTSTANDING", // "OUTSTANDING", "SETTLEMENTS", "ALL"
    val allFriends: List<Friend> = emptyList(),
    val categories: List<Category> = emptyList(),
    val friendGroups: List<FriendGroup> = emptyList(),
    val userPreferences: com.naman.rupeeflow.data.repository.UserPreferences = com.naman.rupeeflow.data.repository.UserPreferences()
)

class FriendDetailViewModel(
    private val friendId: Long,
    private val friendRepository: FriendRepository,
    private val transactionRepository: TransactionRepository,
    private val settlementRepository: SettlementRepository,
    private val categoryRepository: CategoryRepository? = null,
    private val friendGroupRepository: FriendGroupRepository? = null,
    private val userPreferencesRepository: com.naman.rupeeflow.data.repository.UserPreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendDetailUiState())
    val uiState: StateFlow<FriendDetailUiState> = _uiState.asStateFlow()

    init {
        loadFriendDetails()
    }

    private fun loadFriendDetails() {
        viewModelScope.launch {
            val categoriesFlow = categoryRepository?.getAllCategories() ?: flowOf(emptyList())
            val groupsFlow = friendGroupRepository?.getAllGroups() ?: flowOf(emptyList())
            val prefsFlow = userPreferencesRepository?.userPreferencesFlow
                ?: flowOf(com.naman.rupeeflow.data.repository.UserPreferences())

            val baseFlow = combine(
                friendRepository.getFriendByIdFlow(friendId),
                transactionRepository.getAllTransactions(),
                settlementRepository.getSettlementsForFriend(friendId),
                friendRepository.getAllFriends(),
                categoriesFlow
            ) { rawFriend, allTxns, settlements, allFriends, categories ->
                FriendBaseData(rawFriend, allTxns, settlements, allFriends, categories)
            }

            combine(baseFlow, groupsFlow, prefsFlow) { data, groups, prefs ->
                val rawFriend = data.rawFriend ?: return@combine null

                // Compute ledger for this friend
                val friendsWithLedger = LedgerCalculator.computeFriendLedgers(listOf(rawFriend), data.allTxns, data.settlements)
                val computedFriend = friendsWithLedger.firstOrNull() ?: rawFriend

                // Filter related transactions where friend is participant OR payer
                val relatedTxns = data.allTxns.filter { txn ->
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
                val validSettlements = data.settlements.filter { !it.isReversed }
                val received = validSettlements
                    .filter { it.payerId == friendId && it.receiverId == null }
                    .sumOf { it.amountPaise }
                val paid = validSettlements
                    .filter { it.payerId == null && it.receiverId == friendId }
                    .sumOf { it.amountPaise }

                // Compute complete friend expense history and outstanding expenses using FIFO settlement allocation
                val allExpenseHistory = LedgerCalculator.calculateFriendExpenseHistory(friendId, data.allTxns, data.settlements)
                val unsettled = allExpenseHistory.filter { it.remainingOutstandingPaise > 0L }

                FriendDetailUiState(
                    isLoading = false,
                    friend = computedFriend,
                    relatedTransactions = relatedTxns,
                    unsettledExpenses = unsettled,
                    expenseHistory = allExpenseHistory,
                    settlements = data.settlements,
                    totalSharedExpensesPaise = totalShared,
                    totalPaymentsReceivedPaise = received,
                    totalPaymentsMadePaise = paid,
                    remainingBalancePaise = computedFriend.netBalancePaise,
                    filterType = _uiState.value.filterType,
                    allFriends = data.allFriends,
                    categories = data.categories,
                    friendGroups = groups,
                    userPreferences = prefs
                )
            }.collect { state ->
                if (state != null) {
                    _uiState.value = state
                }
            }
        }
    }

    private data class FriendBaseData(
        val rawFriend: Friend?,
        val allTxns: List<Transaction>,
        val settlements: List<Settlement>,
        val allFriends: List<Friend>,
        val categories: List<Category>
    )

    fun setFilterType(filter: String) {
        _uiState.update { it.copy(filterType = filter) }
    }

    fun updateFriend(updatedFriend: Friend) {
        viewModelScope.launch {
            friendRepository.updateFriend(updatedFriend)
        }
    }

    fun updateFriendProfileImage(imagePath: String?) {
        viewModelScope.launch {
            val currentFriend = _uiState.value.friend ?: return@launch
            val updated = currentFriend.copy(profileImagePath = imagePath)
            friendRepository.updateFriend(updated)
        }
    }

    fun recordSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementRepository.recordSettlement(settlement)
        }
    }

    fun updatePersonalUpi(upiId: String?, displayName: String?) {
        viewModelScope.launch {
            userPreferencesRepository?.setPersonalUpiDetails(upiId, displayName)
        }
    }

    // Update existing settlement with ledger recalculation
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

    suspend fun getSuggestedCategoryForMerchant(merchant: String?): Category? {
        if (merchant.isNullOrBlank()) return null
        val dbCatId = transactionRepository.getLatestCategoryForMerchant(merchant)
        if (dbCatId != null && dbCatId > 0L) {
            return _uiState.value.categories.firstOrNull { it.id == dbCatId }
                ?: categoryRepository?.getCategoryById(dbCatId)
        }
        return null
    }

    // Transaction operations
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

    // Safe friend archiving & deletion
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

    val upiSettlementManager = com.naman.rupeeflow.upi.UpiSettlementManager(
        settlementRepository = settlementRepository
    )
}
