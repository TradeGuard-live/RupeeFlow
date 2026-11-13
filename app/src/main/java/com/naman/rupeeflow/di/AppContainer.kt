package com.naman.rupeeflow.di

import android.content.Context
import com.naman.rupeeflow.data.local.ImageStorageManager
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
    val friendGroupRepository: FriendGroupRepository
    val expenseGroupRepository: ExpenseGroupRepository
    val reportDraftRepository: ReportDraftRepository
    val inputRankingRepository: InputRankingRepository
    val imageStorageManager: ImageStorageManager
    val appLockManager: com.naman.rupeeflow.security.AppLockManager
    val merchantPreferenceRepository: MerchantPreferenceRepository
    val smartTransactionMatcher: com.naman.rupeeflow.domain.matching.SmartTransactionMatcher
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: RupeeFlowDatabase by lazy {
        RupeeFlowDatabase.getDatabase(context)
    }

    override val friendGroupRepository: FriendGroupRepository by lazy {
        FriendGroupRepositoryImpl(database.friendGroupDao())
    }

    override val expenseGroupRepository: ExpenseGroupRepository by lazy {
        ExpenseGroupRepositoryImpl(
            expenseGroupDao = database.expenseGroupDao(),
            transactionDao = database.transactionDao(),
            friendGroupDao = database.friendGroupDao()
        )
    }

    override val reportDraftRepository: ReportDraftRepository by lazy {
        ReportDraftRepositoryImpl(database.reportDraftDao())
    }

    override val inputRankingRepository: InputRankingRepository by lazy {
        InputRankingRepositoryImpl(database.inputUsageDao())
    }

    override val imageStorageManager: ImageStorageManager
        get() = ImageStorageManager

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
        SettlementRepositoryImpl(
            settlementDao = database.settlementDao(),
            friendDao = database.friendDao()
        )
    }

    override val pendingTransactionRepository: PendingTransactionRepository by lazy {
        PendingTransactionRepositoryImpl(
            pendingDao = database.pendingTransactionDao(),
            transactionDao = database.transactionDao()
        )
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
            categoryRepository = categoryRepository,
            friendGroupRepository = friendGroupRepository,
            context = context
        )
    }

    override val appLockManager: com.naman.rupeeflow.security.AppLockManager by lazy {
        com.naman.rupeeflow.security.AppLockManager(userPreferencesRepository)
    }

    override val merchantPreferenceRepository: MerchantPreferenceRepository by lazy {
        MerchantPreferenceRepositoryImpl(database.inputUsageDao())
    }

    override val smartTransactionMatcher: com.naman.rupeeflow.domain.matching.SmartTransactionMatcher by lazy {
        com.naman.rupeeflow.domain.matching.SmartTransactionMatcher(merchantPreferenceRepository)
    }
}
