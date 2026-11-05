package com.naman.rupeeflow.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.naman.rupeeflow.RupeeFlowApplication
import com.naman.rupeeflow.ui.screens.analytics.AnalyticsViewModel
import com.naman.rupeeflow.ui.screens.friends.FriendDetailViewModel
import com.naman.rupeeflow.ui.screens.friends.FriendsViewModel
import com.naman.rupeeflow.ui.screens.overview.OverviewViewModel
import com.naman.rupeeflow.ui.screens.pending.PendingTransactionsViewModel
import com.naman.rupeeflow.ui.screens.settings.SettingsViewModel
import com.naman.rupeeflow.ui.screens.transactions.TransactionsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val app = rupeeFlowApplication()
            OverviewViewModel(
                transactionRepository = app.appContainer.transactionRepository,
                friendRepository = app.appContainer.friendRepository,
                settlementRepository = app.appContainer.settlementRepository,
                pendingTransactionRepository = app.appContainer.pendingTransactionRepository,
                categoryRepository = app.appContainer.categoryRepository,
                friendGroupRepository = app.appContainer.friendGroupRepository
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            TransactionsViewModel(
                transactionRepository = app.appContainer.transactionRepository,
                categoryRepository = app.appContainer.categoryRepository,
                friendRepository = app.appContainer.friendRepository,
                settlementRepository = app.appContainer.settlementRepository,
                friendGroupRepository = app.appContainer.friendGroupRepository
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            FriendsViewModel(
                friendRepository = app.appContainer.friendRepository,
                transactionRepository = app.appContainer.transactionRepository,
                settlementRepository = app.appContainer.settlementRepository,
                friendGroupRepository = app.appContainer.friendGroupRepository
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            AnalyticsViewModel(
                transactionRepository = app.appContainer.transactionRepository,
                categoryRepository = app.appContainer.categoryRepository,
                friendRepository = app.appContainer.friendRepository,
                settlementRepository = app.appContainer.settlementRepository
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            SettingsViewModel(
                userPreferencesRepository = app.appContainer.userPreferencesRepository,
                categoryRepository = app.appContainer.categoryRepository,
                backupRestoreRepository = app.appContainer.backupRestoreRepository
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            PendingTransactionsViewModel(
                pendingTransactionRepository = app.appContainer.pendingTransactionRepository,
                transactionRepository = app.appContainer.transactionRepository,
                categoryRepository = app.appContainer.categoryRepository,
                friendRepository = app.appContainer.friendRepository,
                settlementRepository = app.appContainer.settlementRepository,
                friendGroupRepository = app.appContainer.friendGroupRepository
            )
        }
    }

    fun friendDetailFactory(friendId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = rupeeFlowApplication()
            FriendDetailViewModel(
                friendId = friendId,
                friendRepository = app.appContainer.friendRepository,
                transactionRepository = app.appContainer.transactionRepository,
                settlementRepository = app.appContainer.settlementRepository,
                categoryRepository = app.appContainer.categoryRepository,
                friendGroupRepository = app.appContainer.friendGroupRepository
            )
        }
    }
}

fun CreationExtras.rupeeFlowApplication(): RupeeFlowApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RupeeFlowApplication)
