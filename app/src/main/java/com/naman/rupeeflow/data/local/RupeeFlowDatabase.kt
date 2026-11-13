package com.naman.rupeeflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.naman.rupeeflow.data.local.converter.Converters
import com.naman.rupeeflow.data.local.dao.*
import com.naman.rupeeflow.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Database(
    entities = [
        CategoryEntity::class,
        FriendEntity::class,
        TransactionEntity::class,
        TransactionParticipantEntity::class,
        SettlementEntity::class,
        PendingTransactionEntity::class,
        FriendGroupEntity::class,
        FriendGroupMemberEntity::class,
        ExpenseGroupEntity::class,
        ReportDraftEntity::class,
        InputUsageEntity::class
    ],
    version = 7, // Upgraded to version 7: Expense Groups and PDF Report Drafts
    exportSchema = true // Schema export enabled
)
@TypeConverters(Converters::class)
abstract class RupeeFlowDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun friendDao(): FriendDao
    abstract fun transactionDao(): TransactionDao
    abstract fun settlementDao(): SettlementDao
    abstract fun pendingTransactionDao(): PendingTransactionDao
    abstract fun friendGroupDao(): FriendGroupDao
    abstract fun expenseGroupDao(): ExpenseGroupDao
    abstract fun reportDraftDao(): ReportDraftDao
    abstract fun inputUsageDao(): InputUsageDao

    companion object {
        @Volatile
        private var INSTANCE: RupeeFlowDatabase? = null

        // Safe staging-table recreation procedure avoiding SQLite foreign key CASCADE drops
        // Temporary staging tables have NO foreign keys, and child tables are dropped before parent tables,
        // eliminating any cascade deletion risk even when foreign keys are active.
        private fun rebuildTablesSafely(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = OFF;")

            // 1. Create temporary staging tables WITHOUT foreign key constraints
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `temp_participants` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transactionId` INTEGER NOT NULL,
                    `friendId` INTEGER,
                    `shareAmountPaise` INTEGER NOT NULL,
                    `percentage` REAL NOT NULL,
                    `splitMethod` TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO `temp_participants` (`id`, `transactionId`, `friendId`, `shareAmountPaise`, `percentage`, `splitMethod`)
                SELECT `id`, `transactionId`, `friendId`, `shareAmountPaise`, `percentage`, `splitMethod`
                FROM `transaction_participants`
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `temp_transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `amountPaise` INTEGER NOT NULL,
                    `categoryId` INTEGER NOT NULL,
                    `transactionDate` INTEGER NOT NULL,
                    `paymentMethod` TEXT NOT NULL,
                    `accountSource` TEXT,
                    `paidByFriendId` INTEGER,
                    `splitMethod` TEXT NOT NULL,
                    `transactionType` TEXT NOT NULL,
                    `transactionStatus` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `merchantName` TEXT,
                    `referenceNumber` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO `temp_transactions` (
                    `id`, `title`, `description`, `amountPaise`, `categoryId`, `transactionDate`,
                    `paymentMethod`, `accountSource`, `paidByFriendId`, `splitMethod`, `transactionType`,
                    `transactionStatus`, `source`, `merchantName`, `referenceNumber`, `createdAt`, `updatedAt`
                )
                SELECT `id`, `title`, `description`, `amountPaise`, `categoryId`, `transactionDate`,
                    `paymentMethod`, `accountSource`, `paidByFriendId`, `splitMethod`, `transactionType`,
                    `transactionStatus`, `source`, `merchantName`, `referenceNumber`, `createdAt`, `updatedAt`
                FROM `transactions`
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `temp_settlements` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `payerId` INTEGER,
                    `receiverId` INTEGER,
                    `amountPaise` INTEGER NOT NULL,
                    `settlementDate` INTEGER NOT NULL,
                    `paymentMethod` TEXT NOT NULL,
                    `referenceNumber` TEXT,
                    `notes` TEXT,
                    `isReversed` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO `temp_settlements` (
                    `id`, `payerId`, `receiverId`, `amountPaise`, `settlementDate`,
                    `paymentMethod`, `referenceNumber`, `notes`, `isReversed`, `createdAt`
                )
                SELECT `id`, `payerId`, `receiverId`, `amountPaise`, `settlementDate`,
                    `paymentMethod`, `referenceNumber`, `notes`, `isReversed`, `createdAt`
                FROM `settlements`
            """.trimIndent())

            // 2. Drop child tables BEFORE parent tables.
            // Since staging tables have no FKs and child is dropped first, dropping transactions triggers ZERO cascades!
            db.execSQL("DROP TABLE `transaction_participants`")
            db.execSQL("DROP TABLE `transactions`")
            db.execSQL("DROP TABLE `settlements`")

            // 3. Create final transactions table with RESTRICT foreign keys
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `amountPaise` INTEGER NOT NULL,
                    `categoryId` INTEGER NOT NULL,
                    `transactionDate` INTEGER NOT NULL,
                    `paymentMethod` TEXT NOT NULL,
                    `accountSource` TEXT,
                    `paidByFriendId` INTEGER,
                    `splitMethod` TEXT NOT NULL,
                    `transactionType` TEXT NOT NULL,
                    `transactionStatus` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `merchantName` TEXT,
                    `referenceNumber` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`paidByFriendId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO `transactions` (
                    `id`, `title`, `description`, `amountPaise`, `categoryId`, `transactionDate`,
                    `paymentMethod`, `accountSource`, `paidByFriendId`, `splitMethod`, `transactionType`,
                    `transactionStatus`, `source`, `merchantName`, `referenceNumber`, `createdAt`, `updatedAt`
                )
                SELECT `id`, `title`, `description`, `amountPaise`, `categoryId`, `transactionDate`,
                    `paymentMethod`, `accountSource`, `paidByFriendId`, `splitMethod`, `transactionType`,
                    `transactionStatus`, `source`, `merchantName`, `referenceNumber`, `createdAt`, `updatedAt`
                FROM `temp_transactions`
            """.trimIndent())

            // 4. Create final transaction_participants table with CASCADE to transactions and RESTRICT to friends
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `transaction_participants` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `transactionId` INTEGER NOT NULL,
                    `friendId` INTEGER,
                    `shareAmountPaise` INTEGER NOT NULL,
                    `percentage` REAL NOT NULL,
                    `splitMethod` TEXT NOT NULL,
                    FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`friendId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO `transaction_participants` (
                    `id`, `transactionId`, `friendId`, `shareAmountPaise`, `percentage`, `splitMethod`
                )
                SELECT `id`, `transactionId`, `friendId`, `shareAmountPaise`, `percentage`, `splitMethod`
                FROM `temp_participants`
            """.trimIndent())

            // 5. Create final settlements table with RESTRICT foreign keys
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `settlements` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `payerId` INTEGER,
                    `receiverId` INTEGER,
                    `amountPaise` INTEGER NOT NULL,
                    `settlementDate` INTEGER NOT NULL,
                    `paymentMethod` TEXT NOT NULL,
                    `referenceNumber` TEXT,
                    `notes` TEXT,
                    `isReversed` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`payerId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`receiverId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO `settlements` (
                    `id`, `payerId`, `receiverId`, `amountPaise`, `settlementDate`,
                    `paymentMethod`, `referenceNumber`, `notes`, `isReversed`, `createdAt`
                )
                SELECT `id`, `payerId`, `receiverId`, `amountPaise`, `settlementDate`,
                    `paymentMethod`, `referenceNumber`, `notes`, `isReversed`, `createdAt`
                FROM `temp_settlements`
            """.trimIndent())

            // 6. Clean up temporary staging tables
            db.execSQL("DROP TABLE IF EXISTS `temp_participants`")
            db.execSQL("DROP TABLE IF EXISTS `temp_transactions`")
            db.execSQL("DROP TABLE IF EXISTS `temp_settlements`")

            // 7. Recreate indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_paidByFriendId` ON `transactions` (`paidByFriendId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionDate` ON `transactions` (`transactionDate`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionStatus` ON `transactions` (`transactionStatus`)")

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_participants_transactionId` ON `transaction_participants` (`transactionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_participants_friendId` ON `transaction_participants` (`friendId`)")

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_payerId` ON `settlements` (`payerId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_receiverId` ON `settlements` (`receiverId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_settlementDate` ON `settlements` (`settlementDate`)")

            // Re-enable foreign keys
            db.execSQL("PRAGMA foreign_keys = ON;")
        }

        // Migration from version 1 to 2
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                rebuildTablesSafely(db)
            }
        }

        // Corrective migration from version 2 to 3
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                rebuildTablesSafely(db)
            }
        }

        // Direct migration from version 1 to 3
        val MIGRATION_1_3 = object : Migration(1, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                rebuildTablesSafely(db)
            }
        }

        // Migration from version 3 to 4: Add friend_groups and friend_group_members tables
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `friend_groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `iconName` TEXT,
                        `colorHex` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `friend_group_members` (
                        `groupId` INTEGER NOT NULL,
                        `friendId` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`groupId`, `friendId`),
                        FOREIGN KEY(`groupId`) REFERENCES `friend_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`friendId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_friend_group_members_groupId` ON `friend_group_members` (`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_friend_group_members_friendId` ON `friend_group_members` (`friendId`)")
            }
        }

        // Direct migration from version 1 to 4
        val MIGRATION_1_4 = object : Migration(1, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        // Direct migration from version 2 to 4
        val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3.migrate(db)
                MIGRATION_3_4.migrate(db)
            }
        }

        // Migration from version 4 to 5: Add profile/group images, transaction groupId/billImagePath, input_usage table
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add profileImagePath to friends
                db.execSQL("ALTER TABLE `friends` ADD COLUMN `profileImagePath` TEXT")

                // 2. Add groupImagePath to friend_groups
                db.execSQL("ALTER TABLE `friend_groups` ADD COLUMN `groupImagePath` TEXT")

                // 3. Add groupId and billImagePath to transactions
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `groupId` INTEGER REFERENCES `friend_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `billImagePath` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_groupId` ON `transactions` (`groupId`)")

                // 4. Create input_usage table for adaptive smart dynamic ranking
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `input_usage` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fieldType` TEXT NOT NULL,
                        `optionId` TEXT NOT NULL,
                        `usageCount` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL,
                        `preferenceScore` REAL NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_input_usage_fieldType_optionId` ON `input_usage` (`fieldType`, `optionId`)")
            }
        }

        // Direct migration from version 1 to 5
        val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        // Direct migration from version 2 to 5
        val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        // Direct migration from version 3 to 5
        val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4.migrate(db)
                MIGRATION_4_5.migrate(db)
            }
        }

        // Migration from version 5 to 6: Add offline location fields to transactions and pending_transactions
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add location fields to transactions
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `latitude` REAL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `longitude` REAL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `accuracyMeters` REAL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `locationCapturedAt` INTEGER")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `locationSource` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `locationLabel` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `googleMapsUrl` TEXT")

                // 2. Add location fields to pending_transactions
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `latitude` REAL")
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `longitude` REAL")
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `accuracyMeters` REAL")
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `locationCapturedAt` INTEGER")
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `locationSource` TEXT")
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `locationLabel` TEXT")
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `googleMapsUrl` TEXT")
            }
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_2_6 = object : Migration(2, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5.migrate(db)
                MIGRATION_5_6.migrate(db)
            }
        }

        // Migration from version 6 to 7: Add expense_groups, report_drafts, and expense group references
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add description column to friend_groups
                db.execSQL("ALTER TABLE `friend_groups` ADD COLUMN `description` TEXT")

                // 2. Create expense_groups table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `expense_groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `iconName` TEXT,
                        `colorHex` TEXT,
                        `isArchived` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_groups_name` ON `expense_groups` (`name`)")

                // 3. Create report_drafts table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `report_drafts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `periodType` TEXT NOT NULL,
                        `startDate` INTEGER NOT NULL,
                        `endDate` INTEGER NOT NULL,
                        `excludedTransactionIdsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())

                // 4. Add expenseGroupId and expenseGroupName to transactions
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `expenseGroupId` INTEGER REFERENCES `expense_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `expenseGroupName` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_expenseGroupId` ON `transactions` (`expenseGroupId`)")
            }
        }

        val MIGRATION_5_7 = object : Migration(5, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_4_7 = object : Migration(4, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_3_7 = object : Migration(3, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_2_7 = object : Migration(2, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        val MIGRATION_1_7 = object : Migration(1, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_6.migrate(db)
                MIGRATION_6_7.migrate(db)
            }
        }

        // Automatic safe pre-migration backup procedure
        fun createPreMigrationBackup(context: Context) {
            try {
                val dbFile = context.getDatabasePath("rupeeflow_database.db")
                if (dbFile.exists() && dbFile.length() > 0) {
                    val backupFile = File(dbFile.parentFile, "rupeeflow_database_backup.db")
                    if (!backupFile.exists()) {
                        dbFile.copyTo(backupFile, overwrite = false)
                    }
                }
            } catch (_: Exception) {
                // Non-fatal safety hook
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): RupeeFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                createPreMigrationBackup(context.applicationContext)
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RupeeFlowDatabase::class.java,
                    "rupeeflow_database.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_1_3,
                        MIGRATION_3_4, MIGRATION_1_4, MIGRATION_2_4,
                        MIGRATION_4_5, MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5,
                        MIGRATION_5_6, MIGRATION_1_6, MIGRATION_2_6, MIGRATION_3_6, MIGRATION_4_6,
                        MIGRATION_6_7, MIGRATION_5_7, MIGRATION_4_7, MIGRATION_3_7, MIGRATION_2_7, MIGRATION_1_7
                    )
                    .addCallback(DatabaseCallback(scope))
                    // Destructive migration removed! Data preservation guaranteed.
                    .build()
                INSTANCE = instance
                instance
            }
        }

        val DEFAULT_CATEGORIES = listOf(
            CategoryEntity(name = "Food", iconName = "restaurant", colorHex = "#F97316", isDefault = true),
            CategoryEntity(name = "Groceries", iconName = "shopping_cart", colorHex = "#10B981", isDefault = true),
            CategoryEntity(name = "Transportation", iconName = "directions_car", colorHex = "#3B82F6", isDefault = true),
            CategoryEntity(name = "Shopping", iconName = "shopping_bag", colorHex = "#EC4899", isDefault = true),
            CategoryEntity(name = "Education", iconName = "school", colorHex = "#8B5CF6", isDefault = true),
            CategoryEntity(name = "Entertainment", iconName = "movie", colorHex = "#F43F5E", isDefault = true),
            CategoryEntity(name = "Health", iconName = "favorite", colorHex = "#EF4444", isDefault = true),
            CategoryEntity(name = "Rent", iconName = "home", colorHex = "#6366F1", isDefault = true),
            CategoryEntity(name = "Utilities", iconName = "bolt", colorHex = "#EAB308", isDefault = true),
            CategoryEntity(name = "Travel", iconName = "flight", colorHex = "#06B6D4", isDefault = true),
            CategoryEntity(name = "Bills", iconName = "receipt_long", colorHex = "#14B8A6", isDefault = true),
            CategoryEntity(name = "Subscriptions", iconName = "subscriptions", colorHex = "#A855F7", isDefault = true),
            CategoryEntity(name = "Personal", iconName = "person", colorHex = "#64748B", isDefault = true),
            CategoryEntity(name = "Friends", iconName = "group", colorHex = "#38BDF8", isDefault = true),
            CategoryEntity(name = "Other", iconName = "category", colorHex = "#94A3B8", isDefault = true)
        )

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateDefaultCategories(database.categoryDao())
                    }
                }
            }

            private suspend fun populateDefaultCategories(categoryDao: CategoryDao) {
                if (categoryDao.getCount() == 0) {
                    categoryDao.insertCategories(DEFAULT_CATEGORIES)
                }
            }
        }
    }
}
