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
import com.naman.rupeeflow.voice.viewmodel.VoiceAssistantViewModel

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
                friendGroupRepository = app.appContainer.friendGroupRepository,
                expenseGroupRepository = app.appContainer.expenseGroupRepository,
                inputRankingRepository = app.appContainer.inputRankingRepository
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            TransactionsViewModel(
                transactionRepository = app.appContainer.transactionRepository,
                categoryRepository = app.appContainer.categoryRepository,
                friendRepository = app.appContainer.friendRepository,
                settlementRepository = app.appContainer.settlementRepository,
                friendGroupRepository = app.appContainer.friendGroupRepository,
                expenseGroupRepository = app.appContainer.expenseGroupRepository,
                inputRankingRepository = app.appContainer.inputRankingRepository,
                pendingTransactionRepository = app.appContainer.pendingTransactionRepository
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
                backupRestoreRepository = app.appContainer.backupRestoreRepository,
                transactionRepository = app.appContainer.transactionRepository,
                pendingTransactionRepository = app.appContainer.pendingTransactionRepository
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
                friendGroupRepository = app.appContainer.friendGroupRepository,
                merchantPreferenceRepository = app.appContainer.merchantPreferenceRepository,
                smartTransactionMatcher = app.appContainer.smartTransactionMatcher,
                userPreferencesRepository = app.appContainer.userPreferencesRepository
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            VoiceAssistantViewModel(
                context = app.applicationContext,
                transactionRepository = app.appContainer.transactionRepository,
                friendRepository = app.appContainer.friendRepository,
                settlementRepository = app.appContainer.settlementRepository,
                categoryRepository = app.appContainer.categoryRepository,
                friendGroupRepository = app.appContainer.friendGroupRepository
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            com.naman.rupeeflow.ui.screens.lock.AppLockViewModel(
                appLockManager = app.appContainer.appLockManager
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            com.naman.rupeeflow.ui.screens.groups.ExpenseGroupViewModel(
                expenseGroupRepository = app.appContainer.expenseGroupRepository,
                transactionRepository = app.appContainer.transactionRepository,
                friendRepository = app.appContainer.friendRepository,
                friendGroupRepository = app.appContainer.friendGroupRepository,
                settlementRepository = app.appContainer.settlementRepository,
                categoryRepository = app.appContainer.categoryRepository
            )
        }
        initializer {
            val app = rupeeFlowApplication()
            com.naman.rupeeflow.ui.screens.report.ReportGeneratorViewModel(
                transactionRepository = app.appContainer.transactionRepository,
                categoryRepository = app.appContainer.categoryRepository,
                reportDraftRepository = app.appContainer.reportDraftRepository,
                expenseGroupRepository = app.appContainer.expenseGroupRepository
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
