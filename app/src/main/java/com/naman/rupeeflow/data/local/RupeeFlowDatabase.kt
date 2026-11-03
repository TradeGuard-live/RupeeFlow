package com.naman.rupeeflow.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.naman.rupeeflow.data.local.converter.Converters
import com.naman.rupeeflow.data.local.dao.*
import com.naman.rupeeflow.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CategoryEntity::class,
        FriendEntity::class,
        TransactionEntity::class,
        TransactionParticipantEntity::class,
        SettlementEntity::class,
        PendingTransactionEntity::class
    ],
    version = 1,
    exportSchema = false
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

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): RupeeFlowDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RupeeFlowDatabase::class.java,
                    "rupeeflow_database.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
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
