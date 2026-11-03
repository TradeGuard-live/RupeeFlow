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
        PendingTransactionEntity::class
    ],
    version = 2, // ✅ Upgraded to version 2 with safe Room migration
    exportSchema = true // ✅ Schema export enabled
)
@TypeConverters(Converters::class)
abstract class RupeeFlowDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun friendDao(): FriendDao
    abstract fun transactionDao(): TransactionDao
    abstract fun settlementDao(): SettlementDao
    abstract fun pendingTransactionDao(): PendingTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: RupeeFlowDatabase? = null

        // ✅ Migration from version 1 to 2
        // Updates foreign key rules to RESTRICT to prevent friend identity collisions,
        // safely copying all existing records across transactions, participants, and settlements.
        // Explicitly turns foreign keys OFF during migration and copies participants prior to table drops
        // to prevent cascade deletions.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Disable foreign key constraints during table recreate to prevent cascade drops
                db.execSQL("PRAGMA foreign_keys = OFF;")

                // 1. Transaction Participants table (create & copy first to safeguard children)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transaction_participants_new` (
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
                    INSERT INTO `transaction_participants_new` (
                        `id`, `transactionId`, `friendId`, `shareAmountPaise`, `percentage`, `splitMethod`
                    )
                    SELECT `id`, `transactionId`, `friendId`, `shareAmountPaise`, `percentage`, `splitMethod`
                    FROM `transaction_participants`
                """.trimIndent())

                // 2. Transactions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transactions_new` (
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
                    INSERT INTO `transactions_new` (
                        `id`, `title`, `description`, `amountPaise`, `categoryId`, `transactionDate`,
                        `paymentMethod`, `accountSource`, `paidByFriendId`, `splitMethod`, `transactionType`,
                        `transactionStatus`, `source`, `merchantName`, `referenceNumber`, `createdAt`, `updatedAt`
                    )
                    SELECT `id`, `title`, `description`, `amountPaise`, `categoryId`, `transactionDate`,
                        `paymentMethod`, `accountSource`, `paidByFriendId`, `splitMethod`, `transactionType`,
                        `transactionStatus`, `source`, `merchantName`, `referenceNumber`, `createdAt`, `updatedAt`
                    FROM `transactions`
                """.trimIndent())

                // 3. Settlements table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `settlements_new` (
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
                    INSERT INTO `settlements_new` (
                        `id`, `payerId`, `receiverId`, `amountPaise`, `settlementDate`,
                        `paymentMethod`, `referenceNumber`, `notes`, `isReversed`, `createdAt`
                    )
                    SELECT `id`, `payerId`, `receiverId`, `amountPaise`, `settlementDate`,
                        `paymentMethod`, `referenceNumber`, `notes`, `isReversed`, `createdAt`
                    FROM `settlements`
                """.trimIndent())

                // Drop old tables
                db.execSQL("DROP TABLE `transaction_participants`")
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("DROP TABLE `settlements`")

                // Rename new tables to standard names
                db.execSQL("ALTER TABLE `transaction_participants_new` RENAME TO `transaction_participants`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")
                db.execSQL("ALTER TABLE `settlements_new` RENAME TO `settlements`")

                // Recreate indexes
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_paidByFriendId` ON `transactions` (`paidByFriendId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionDate` ON `transactions` (`transactionDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionStatus` ON `transactions` (`transactionStatus`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_participants_transactionId` ON `transaction_participants` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_participants_friendId` ON `transaction_participants` (`friendId`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_payerId` ON `settlements` (`payerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_receiverId` ON `settlements` (`receiverId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlements_settlementDate` ON `settlements` (`settlementDate`)")

                // Re-enable foreign key constraints
                db.execSQL("PRAGMA foreign_keys = ON;")
            }
        }

        // ✅ Automatic safe pre-migration backup procedure
        fun createPreMigrationBackup(context: Context) {
            try {
                val dbFile = context.getDatabasePath("rupeeflow_database.db")
                if (dbFile.exists() && dbFile.length() > 0) {
                    val backupFile = File(dbFile.parentFile, "rupeeflow_database_pre_v2_backup.db")
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
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(DatabaseCallback(scope))
                    // ✅ Destructive migration removed! Data preservation guaranteed.
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
