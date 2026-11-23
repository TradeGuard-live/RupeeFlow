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
        InputUsageEntity::class,
        GroupActivityEntity::class,
        SavingsGoalEntity::class,
        SavingsEntryEntity::class,
        ExpenseAllocationEntity::class,
        TransactionItemEntity::class,
        TransactionItemAllocationEntity::class
    ],
    version = 16, // Upgraded to version 16: Item-Wise Expense Splitting (transaction_items & transaction_item_allocations)
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
    abstract fun groupActivityDao(): GroupActivityDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun savingsEntryDao(): SavingsEntryDao
    abstract fun expenseAllocationDao(): ExpenseAllocationDao

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

        // Migration from version 7 to 8: Add phoneNumber and upiId to pending_transactions and transactions
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `phoneNumber` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `upiId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `phoneNumber` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `upiId` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_6_8 = object : Migration(6, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        val MIGRATION_5_8 = object : Migration(5, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        val MIGRATION_1_8 = object : Migration(1, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_7.migrate(db)
                MIGRATION_7_8.migrate(db)
            }
        }

        // Migration from version 8 to 9: Add trip collaboration metadata to expense_groups
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `destination` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `startDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `endDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `coverImageUri` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `linkedFriendGroupId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `archivedAt` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_groups_linkedFriendGroupId` ON `expense_groups` (`linkedFriendGroupId`)")
            }
        }

        val MIGRATION_7_9 = object : Migration(7, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8.migrate(db)
                MIGRATION_8_9.migrate(db)
            }
        }

        val MIGRATION_6_9 = object : Migration(6, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_8.migrate(db)
                MIGRATION_8_9.migrate(db)
            }
        }

        val MIGRATION_5_9 = object : Migration(5, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_8.migrate(db)
                MIGRATION_8_9.migrate(db)
            }
        }

        val MIGRATION_1_9 = object : Migration(1, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_8.migrate(db)
                MIGRATION_8_9.migrate(db)
            }
        }

        // Migration from version 9 to 10: Split Pro group taxonomy, settlement links, and group activities
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `groupType` TEXT NOT NULL DEFAULT 'CUSTOM'")
                db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `currencyCode` TEXT NOT NULL DEFAULT 'INR'")
                db.execSQL("ALTER TABLE `expense_groups` ADD COLUMN `simplifyDebts` INTEGER NOT NULL DEFAULT 1")

                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `groupId` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `proofImagePath` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_groupId` ON `settlements` (`groupId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `group_activities` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `groupId` INTEGER NOT NULL,
                        `activityType` TEXT NOT NULL,
                        `actorName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `amountPaise` INTEGER DEFAULT NULL,
                        `targetId` INTEGER DEFAULT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`groupId`) REFERENCES `expense_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_activities_groupId` ON `group_activities` (`groupId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_activities_timestamp` ON `group_activities` (`timestamp`)")
            }
        }

        val MIGRATION_8_10 = object : Migration(8, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_8_9.migrate(db)
                MIGRATION_9_10.migrate(db)
            }
        }

        val MIGRATION_7_10 = object : Migration(7, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8.migrate(db)
                MIGRATION_8_9.migrate(db)
                MIGRATION_9_10.migrate(db)
            }
        }

        val MIGRATION_6_10 = object : Migration(6, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_8.migrate(db)
                MIGRATION_8_9.migrate(db)
                MIGRATION_9_10.migrate(db)
            }
        }

        val MIGRATION_5_10 = object : Migration(5, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_8.migrate(db)
                MIGRATION_8_9.migrate(db)
                MIGRATION_9_10.migrate(db)
            }
        }

        val MIGRATION_1_10 = object : Migration(1, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_8.migrate(db)
                MIGRATION_8_9.migrate(db)
                MIGRATION_9_10.migrate(db)
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `savings_goals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `targetAmountPaise` INTEGER NOT NULL,
                        `targetDate` INTEGER,
                        `category` TEXT NOT NULL,
                        `imageUri` TEXT,
                        `colorHex` TEXT NOT NULL,
                        `priority` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `currentSavedPaise` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_status` ON `savings_goals` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goals_category` ON `savings_goals` (`category`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `savings_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `goalId` INTEGER NOT NULL,
                        `amountPaise` INTEGER NOT NULL,
                        `entryType` TEXT NOT NULL,
                        `entryDate` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `description` TEXT NOT NULL,
                        `paymentMethod` TEXT,
                        `note` TEXT,
                        FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_entries_goalId` ON `savings_entries` (`goalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_entries_entryDate` ON `savings_entries` (`entryDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_entries_entryType` ON `savings_entries` (`entryType`)")
            }
        }

        val MIGRATION_9_11 = object : Migration(9, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_9_10.migrate(db)
                MIGRATION_10_11.migrate(db)
            }
        }

        val MIGRATION_8_11 = object : Migration(8, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_8_10.migrate(db)
                MIGRATION_10_11.migrate(db)
            }
        }

        val MIGRATION_7_11 = object : Migration(7, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_10.migrate(db)
                MIGRATION_10_11.migrate(db)
            }
        }

        val MIGRATION_6_11 = object : Migration(6, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_10.migrate(db)
                MIGRATION_10_11.migrate(db)
            }
        }

        val MIGRATION_5_11 = object : Migration(5, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_10.migrate(db)
                MIGRATION_10_11.migrate(db)
            }
        }

        val MIGRATION_1_11 = object : Migration(1, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_10.migrate(db)
                MIGRATION_10_11.migrate(db)
            }
        }

        // Migration from version 11 to 12: Independent Merchant Identity, Storage and Category Learning
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `merchantNormalized` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `rawMerchant` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `sourcePackage` TEXT DEFAULT NULL")

                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `rawMerchant` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `merchantNormalized` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `sourcePackage` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_10_12 = object : Migration(10, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_10_11.migrate(db)
                MIGRATION_11_12.migrate(db)
            }
        }

        val MIGRATION_9_12 = object : Migration(9, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_9_11.migrate(db)
                MIGRATION_11_12.migrate(db)
            }
        }

        val MIGRATION_8_12 = object : Migration(8, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_8_11.migrate(db)
                MIGRATION_11_12.migrate(db)
            }
        }

        val MIGRATION_7_12 = object : Migration(7, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_11.migrate(db)
                MIGRATION_11_12.migrate(db)
            }
        }

        val MIGRATION_6_12 = object : Migration(6, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_11.migrate(db)
                MIGRATION_11_12.migrate(db)
            }
        }

        val MIGRATION_5_12 = object : Migration(5, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_11.migrate(db)
                MIGRATION_11_12.migrate(db)
            }
        }

        val MIGRATION_1_12 = object : Migration(1, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_11.migrate(db)
                MIGRATION_11_12.migrate(db)
            }
        }

        // Migration from version 12 to 13: Split Pro Direct UPI Settlement Integration
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Friend UPI profile fields
                db.execSQL("ALTER TABLE `friends` ADD COLUMN `upiId` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `friends` ADD COLUMN `upiVerifiedName` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `friends` ADD COLUMN `upiVerificationStatus` TEXT NOT NULL DEFAULT 'UNVERIFIED'")
                db.execSQL("ALTER TABLE `friends` ADD COLUMN `upiLastUpdated` INTEGER DEFAULT NULL")

                // Settlement UPI payment state machine fields
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `upiPaymentStatus` TEXT NOT NULL DEFAULT 'SUCCESS'")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `upiVerificationStatus` TEXT NOT NULL DEFAULT 'MANUAL_UNVERIFIED'")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `upiTransactionRef` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `upiResponseRaw` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `upiPayerVpa` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `settlements` ADD COLUMN `upiReceiverVpa` TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_13 = object : Migration(11, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_11_12.migrate(db)
                MIGRATION_12_13.migrate(db)
            }
        }

        val MIGRATION_10_13 = object : Migration(10, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_10_12.migrate(db)
                MIGRATION_12_13.migrate(db)
            }
        }

        val MIGRATION_9_13 = object : Migration(9, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_9_12.migrate(db)
                MIGRATION_12_13.migrate(db)
            }
        }

        val MIGRATION_8_13 = object : Migration(8, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_8_12.migrate(db)
                MIGRATION_12_13.migrate(db)
            }
        }

        val MIGRATION_7_13 = object : Migration(7, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_12.migrate(db)
                MIGRATION_12_13.migrate(db)
            }
        }

        val MIGRATION_6_13 = object : Migration(6, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_12.migrate(db)
                MIGRATION_12_13.migrate(db)
            }
        }

        val MIGRATION_5_13 = object : Migration(5, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_12.migrate(db)
                MIGRATION_12_13.migrate(db)
            }
        }

        val MIGRATION_1_13 = object : Migration(1, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_12.migrate(db)
                MIGRATION_12_13.migrate(db)
            }
        }

        // Migration from version 13 to 14: Merchant Name & UPI Reference Number Auto-Extraction
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add upiReferenceNumber to transactions
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `upiReferenceNumber` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_upiReferenceNumber` ON `transactions` (`upiReferenceNumber`)")

                // Add upiReferenceNumber to pending_transactions
                db.execSQL("ALTER TABLE `pending_transactions` ADD COLUMN `upiReferenceNumber` TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_transactions_upiReferenceNumber` ON `pending_transactions` (`upiReferenceNumber`)")
            }
        }

        val MIGRATION_12_14 = object : Migration(12, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_12_13.migrate(db)
                MIGRATION_13_14.migrate(db)
            }
        }

        val MIGRATION_11_14 = object : Migration(11, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_11_13.migrate(db)
                MIGRATION_13_14.migrate(db)
            }
        }

        val MIGRATION_10_14 = object : Migration(10, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_10_13.migrate(db)
                MIGRATION_13_14.migrate(db)
            }
        }

        val MIGRATION_9_14 = object : Migration(9, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_9_13.migrate(db)
                MIGRATION_13_14.migrate(db)
            }
        }

        val MIGRATION_8_14 = object : Migration(8, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_8_13.migrate(db)
                MIGRATION_13_14.migrate(db)
            }
        }

        val MIGRATION_7_14 = object : Migration(7, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_13.migrate(db)
                MIGRATION_13_14.migrate(db)
            }
        }

        val MIGRATION_6_14 = object : Migration(6, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_13.migrate(db)
                MIGRATION_13_14.migrate(db)
            }
        }

        val MIGRATION_5_14 = object : Migration(5, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_13.migrate(db)
                MIGRATION_13_14.migrate(db)
            }
        }

        val MIGRATION_1_14 = object : Migration(1, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_13.migrate(db)
                MIGRATION_13_14.migrate(db)
            }
        }

        // Migration from version 14 to 15: Expense Spreading & Daily Expense Allocation
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add expense spreading columns to transactions table
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isSpread` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `spreadDurationDays` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `spreadStartDate` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `spreadEndDate` INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_isSpread` ON `transactions` (`isSpread`)")

                // Create expense_allocations table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `expense_allocations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `allocationDate` INTEGER NOT NULL,
                        `amountPaise` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_allocations_transactionId` ON `expense_allocations` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_allocations_allocationDate` ON `expense_allocations` (`allocationDate`)")
            }
        }

        val MIGRATION_13_15 = object : Migration(13, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_13_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        val MIGRATION_12_15 = object : Migration(12, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_12_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        val MIGRATION_11_15 = object : Migration(11, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_11_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        val MIGRATION_10_15 = object : Migration(10, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_10_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        val MIGRATION_9_15 = object : Migration(9, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_9_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        val MIGRATION_8_15 = object : Migration(8, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_8_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        val MIGRATION_7_15 = object : Migration(7, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        val MIGRATION_6_15 = object : Migration(6, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        val MIGRATION_5_15 = object : Migration(5, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        val MIGRATION_1_15 = object : Migration(1, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_14.migrate(db)
                MIGRATION_14_15.migrate(db)
            }
        }

        // Migration from version 15 to 16: Item-Wise Expense Splitting
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transaction_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `quantity` INTEGER NOT NULL DEFAULT 1,
                        `unitPricePaise` INTEGER NOT NULL,
                        `totalAmountPaise` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_items_transactionId` ON `transaction_items` (`transactionId`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transaction_item_allocations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `friendId` INTEGER,
                        `allocatedAmountPaise` INTEGER NOT NULL,
                        FOREIGN KEY(`itemId`) REFERENCES `transaction_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`friendId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_item_allocations_itemId` ON `transaction_item_allocations` (`itemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_item_allocations_transactionId` ON `transaction_item_allocations` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_item_allocations_friendId` ON `transaction_item_allocations` (`friendId`)")
            }
        }

        val MIGRATION_14_16 = object : Migration(14, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_14_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_13_16 = object : Migration(13, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_13_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_12_16 = object : Migration(12, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_12_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_11_16 = object : Migration(11, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_11_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_10_16 = object : Migration(10, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_10_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_9_16 = object : Migration(9, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_9_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_8_16 = object : Migration(8, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_8_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_7_16 = object : Migration(7, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_6_16 = object : Migration(6, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_5_16 = object : Migration(5, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_15.migrate(db)
                MIGRATION_15_16.migrate(db)
            }
        }

        val MIGRATION_1_16 = object : Migration(1, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_15.migrate(db)
                MIGRATION_15_16.migrate(db)
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
                        MIGRATION_6_7, MIGRATION_5_7, MIGRATION_4_7, MIGRATION_3_7, MIGRATION_2_7, MIGRATION_1_7,
                        MIGRATION_7_8, MIGRATION_6_8, MIGRATION_5_8, MIGRATION_1_8,
                        MIGRATION_8_9, MIGRATION_7_9, MIGRATION_6_9, MIGRATION_5_9, MIGRATION_1_9,
                        MIGRATION_9_10, MIGRATION_8_10, MIGRATION_7_10, MIGRATION_6_10, MIGRATION_5_10, MIGRATION_1_10,
                        MIGRATION_10_11, MIGRATION_9_11, MIGRATION_8_11, MIGRATION_7_11, MIGRATION_6_11, MIGRATION_5_11, MIGRATION_1_11,
                        MIGRATION_11_12, MIGRATION_10_12, MIGRATION_9_12, MIGRATION_8_12, MIGRATION_7_12, MIGRATION_6_12, MIGRATION_5_12, MIGRATION_1_12,
                        MIGRATION_12_13, MIGRATION_11_13, MIGRATION_10_13, MIGRATION_9_13, MIGRATION_8_13, MIGRATION_7_13, MIGRATION_6_13, MIGRATION_5_13, MIGRATION_1_13,
                        MIGRATION_13_14, MIGRATION_12_14, MIGRATION_11_14, MIGRATION_10_14, MIGRATION_9_14, MIGRATION_8_14, MIGRATION_7_14, MIGRATION_6_14, MIGRATION_5_14, MIGRATION_1_14,
                        MIGRATION_14_15, MIGRATION_13_15, MIGRATION_12_15, MIGRATION_11_15, MIGRATION_10_15, MIGRATION_9_15, MIGRATION_8_15, MIGRATION_7_15, MIGRATION_6_15, MIGRATION_5_15, MIGRATION_1_15,
                        MIGRATION_15_16, MIGRATION_14_16, MIGRATION_13_16, MIGRATION_12_16, MIGRATION_11_16, MIGRATION_10_16, MIGRATION_9_16, MIGRATION_8_16, MIGRATION_7_16, MIGRATION_6_16, MIGRATION_5_16, MIGRATION_1_16
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
