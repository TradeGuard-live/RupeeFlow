package com.naman.rupeeflow.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.FriendGroupRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendBalanceStatus
import com.naman.rupeeflow.domain.model.FriendGroup
import com.naman.rupeeflow.domain.model.Settlement
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.domain.util.safeSumOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FriendsUiState(
    val isLoading: Boolean = true,
    val friends: List<Friend> = emptyList(),
    val filteredFriends: List<Friend> = emptyList(),
    val groups: List<FriendGroup> = emptyList(),
    val selectedGroupId: Long? = null, // null = All Friends
    val searchQuery: String = "",
    val filterStatus: String = "ALL", // "ALL", "THEY_OWE", "I_OWE", "SETTLED", "ARCHIVED"
    val totalTheyOweMePaise: Long = 0L,
    val totalIOweThemPaise: Long = 0L,
    val netBalancePaise: Long = 0L
)

class FriendsViewModel(
    private val friendRepository: FriendRepository,
    private val transactionRepository: TransactionRepository,
    private val settlementRepository: SettlementRepository,
    private val friendGroupRepository: FriendGroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    private var cachedData: RawLedgerData? = null

    init {
        loadFriendsAndGroups()
    }

    private fun loadFriendsAndGroups() {
        viewModelScope.launch {
            combine(
                friendRepository.getAllFriends(),
                transactionRepository.getAllTransactions(),
                settlementRepository.getAllSettlements(),
                friendGroupRepository.getAllGroups()
            ) { friends, transactions, settlements, groups ->
                RawLedgerData(friends, transactions, settlements, groups)
            }.collect { data ->
                cachedData = data
                _uiState.update { current ->
                    recalculateUiState(
                        current = current,
                        data = data,
                        selectedGroupId = current.selectedGroupId,
                        query = current.searchQuery,
                        filter = current.filterStatus
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            cachedData?.let { data ->
                recalculateUiState(
                    current = current,
                    data = data,
                    selectedGroupId = current.selectedGroupId,
                    query = query,
                    filter = current.filterStatus
                )
            } ?: current.copy(searchQuery = query)
        }
    }

    fun onFilterStatusSelected(filter: String) {
        _uiState.update { current ->
            cachedData?.let { data ->
                recalculateUiState(
                    current = current,
                    data = data,
                    selectedGroupId = current.selectedGroupId,
                    query = current.searchQuery,
                    filter = filter
                )
            } ?: current.copy(filterStatus = filter)
        }
    }

    // ✅ Select group filter (null represents "All Friends") - dynamically updates ledger totals
    fun selectGroup(groupId: Long?) {
        _uiState.update { current ->
            cachedData?.let { data ->
                recalculateUiState(
                    current = current,
                    data = data,
                    selectedGroupId = groupId,
                    query = current.searchQuery,
                    filter = current.filterStatus
                )
            } ?: current.copy(selectedGroupId = groupId)
        }
    }

    private fun recalculateUiState(
        current: FriendsUiState,
        data: RawLedgerData,
        selectedGroupId: Long?,
        query: String,
        filter: String
    ): FriendsUiState {
        val group = if (selectedGroupId != null) data.groups.firstOrNull { it.id == selectedGroupId } else null
        val groupMemberIds = group?.memberFriendIds?.toSet()

        val ledgerResult = LedgerCalculator.computeGroupLedger(
            groupId = selectedGroupId,
            groupMemberIds = groupMemberIds,
            allFriends = data.friends,
            transactions = data.transactions,
            settlements = data.settlements
        )

        val filtered = applyFilters(
            friends = ledgerResult.friendsWithLedger,
            query = query,
            filter = filter
        )

        return current.copy(
            isLoading = false,
            friends = ledgerResult.friendsWithLedger,
            groups = data.groups,
            selectedGroupId = selectedGroupId,
            filteredFriends = filtered,
            searchQuery = query,
            filterStatus = filter,
            totalTheyOweMePaise = ledgerResult.totalTheyOweMePaise,
            totalIOweThemPaise = ledgerResult.totalIOweThemPaise,
            netBalancePaise = ledgerResult.netBalancePaise
        )
    }

    private fun applyFilters(
        friends: List<Friend>,
        query: String,
        filter: String
    ): List<Friend> {
        return friends.filter { friend ->
            val matchesQuery = query.isBlank() ||
                    friend.name.contains(query, ignoreCase = true) ||
                    (friend.phoneNumber?.contains(query, ignoreCase = true) == true)

            val matchesFilter = when (filter) {
                "THEY_OWE" -> !friend.isArchived && friend.status == FriendBalanceStatus.THEY_OWE_ME
                "I_OWE" -> !friend.isArchived && friend.status == FriendBalanceStatus.I_OWE_THEM
                "SETTLED" -> !friend.isArchived && friend.status == FriendBalanceStatus.SETTLED
                "ARCHIVED" -> friend.isArchived
                else -> !friend.isArchived // "ALL" shows all active friends
            }

            matchesQuery && matchesFilter
        }
    }

    fun addFriend(name: String, phone: String?, email: String?, notes: String?, profileImagePath: String? = null) {
        viewModelScope.launch {
            val friend = Friend(
                name = name.trim(),
                phoneNumber = phone?.trim()?.takeIf { it.isNotBlank() },
                email = email?.trim()?.takeIf { it.isNotBlank() },
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                profileImagePath = profileImagePath
            )
            val newFriendId = friendRepository.addFriend(friend)

            // If a group is currently selected, also add the friend to this group
            val currentGroupId = _uiState.value.selectedGroupId
            if (currentGroupId != null && newFriendId > 0) {
                friendGroupRepository.addFriendToGroup(currentGroupId, newFriendId)
            }
        }
    }

    fun updateFriend(friend: Friend) {
        viewModelScope.launch {
            friendRepository.updateFriend(friend)
        }
    }

    // ✅ Group management operations
    fun createGroup(
        name: String,
        iconName: String? = null,
        colorHex: String? = null,
        friendIds: List<Long> = emptyList(),
        groupImagePath: String? = null
    ) {
        viewModelScope.launch {
            val newGroupId = friendGroupRepository.createGroup(name, iconName, colorHex, friendIds, groupImagePath)
            selectGroup(newGroupId)
        }
    }

    fun updateGroup(group: FriendGroup, friendIds: List<Long>) {
        viewModelScope.launch {
            friendGroupRepository.updateGroup(group, friendIds)
        }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            if (_uiState.value.selectedGroupId == groupId) {
                selectGroup(null)
            }
            friendGroupRepository.deleteGroup(groupId)
        }
    }

    // ✅ Safe friend deletion & archiving
    fun deleteFriend(friend: Friend) {
        viewModelScope.launch {
            friendRepository.deleteFriend(friend)
        }
    }

    suspend fun isFriendReferenced(friendId: Long): Boolean {
        return friendRepository.isFriendReferenced(friendId)
    }

    fun recordSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementRepository.recordSettlement(settlement)
        }
    }

    // ✅ Edit existing settlement
    fun updateSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementRepository.updateSettlement(settlement)
        }
    }

    private data class RawLedgerData(
        val friends: List<Friend>,
        val transactions: List<Transaction>,
        val settlements: List<Settlement>,
        val groups: List<FriendGroup>
    )
}
