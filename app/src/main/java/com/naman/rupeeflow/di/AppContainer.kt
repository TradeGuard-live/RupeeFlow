package com.naman.rupeeflow.di

import android.content.Context
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.repository.*

interface AppContainer {
    val database: RupeeFlowDatabase
    val categoryRepository: CategoryRepository
    val friendRepository: FriendRepository
    val transactionRepository: TransactionRepository
    val settlementRepository: SettlementRepository
    val pendingTransactionRepository: PendingTransactionRepository
    val userPreferencesRepository: UserPreferencesRepository
    val backupRestoreRepository: BackupRestoreRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: RupeeFlowDatabase by lazy {
        RupeeFlowDatabase.getDatabase(context)
    }

    override val categoryRepository: CategoryRepository by lazy {
        CategoryRepositoryImpl(database.categoryDao())
    }

    override val friendRepository: FriendRepository by lazy {
        FriendRepositoryImpl(database.friendDao())
    }

    override val transactionRepository: TransactionRepository by lazy {
        TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao()
        )
    }

    override val settlementRepository: SettlementRepository by lazy {
        SettlementRepositoryImpl(database.settlementDao())
    }

    override val pendingTransactionRepository: PendingTransactionRepository by lazy {
        PendingTransactionRepositoryImpl(database.pendingTransactionDao())
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    override val backupRestoreRepository: BackupRestoreRepository by lazy {
        BackupRestoreRepository(
            database = database,
            transactionRepository = transactionRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            categoryRepository = categoryRepository
        )
    }
}
