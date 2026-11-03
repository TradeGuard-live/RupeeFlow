package com.naman.rupeeflow.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendBalanceStatus
import com.naman.rupeeflow.domain.model.Settlement
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FriendsUiState(
    val isLoading: Boolean = true,
    val friends: List<Friend> = emptyList(),
    val filteredFriends: List<Friend> = emptyList(),
    val searchQuery: String = "",
    val filterStatus: String = "ALL", // "ALL", "THEY_OWE", "I_OWE", "SETTLED"
    val totalTheyOweMePaise: Long = 0L,
    val totalIOweThemPaise: Long = 0L,
    val netBalancePaise: Long = 0L
)

class FriendsViewModel(
    private val friendRepository: FriendRepository,
    private val transactionRepository: TransactionRepository,
    private val settlementRepository: SettlementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    init {
        loadFriends()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            combine(
                friendRepository.getAllFriends(),
                transactionRepository.getAllTransactions(),
                settlementRepository.getAllSettlements()
            ) { friends, transactions, settlements ->
                val friendsWithLedger = LedgerCalculator.computeFriendLedgers(friends, transactions, settlements)

                val activeFriends = friendsWithLedger.filter { !it.isArchived }
                val theyOwe = activeFriends.filter { it.netBalancePaise > 0 }.sumOf { it.netBalancePaise }
                val iOwe = activeFriends.filter { it.netBalancePaise < 0 }.sumOf { kotlin.math.abs(it.netBalancePaise) }

                Triple(friendsWithLedger, theyOwe, iOwe)
            }.collect { (friendsWithLedger, theyOwe, iOwe) ->
                _uiState.update { current ->
                    val filtered = applyFilters(friendsWithLedger, current.searchQuery, current.filterStatus)
                    current.copy(
                        isLoading = false,
                        friends = friendsWithLedger,
                        filteredFriends = filtered,
                        totalTheyOweMePaise = theyOwe,
                        totalIOweThemPaise = iOwe,
                        netBalancePaise = theyOwe - iOwe
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilters(current.friends, query, current.filterStatus)
            current.copy(searchQuery = query, filteredFriends = filtered)
        }
    }

    fun onFilterStatusSelected(filter: String) {
        _uiState.update { current ->
            val filtered = applyFilters(current.friends, current.searchQuery, filter)
            current.copy(filterStatus = filter, filteredFriends = filtered)
        }
    }

    private fun applyFilters(friends: List<Friend>, query: String, filter: String): List<Friend> {
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

    fun addFriend(name: String, phone: String?, email: String?, notes: String?) {
        viewModelScope.launch {
            val friend = Friend(
                name = name.trim(),
                phoneNumber = phone?.trim()?.takeIf { it.isNotBlank() },
                email = email?.trim()?.takeIf { it.isNotBlank() },
                notes = notes?.trim()?.takeIf { it.isNotBlank() }
            )
            friendRepository.addFriend(friend)
        }
    }

    fun recordSettlement(settlement: Settlement) {
        viewModelScope.launch {
            settlementRepository.recordSettlement(settlement)
        }
    }
}
