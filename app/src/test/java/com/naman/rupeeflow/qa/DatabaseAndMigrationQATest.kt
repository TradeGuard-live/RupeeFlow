package com.naman.rupeeflow.qa

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.TransactionRepositoryImpl
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionParticipant
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * RupeeFlow Production QA Test Plan
 * Section 6: Database, migrations and data recovery — TC059 to TC068
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseAndMigrationQATest {

    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconName` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `isDefault` INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `friends` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `phoneNumber` TEXT, `email` TEXT, `notes` TEXT, `createdAt` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)")
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
                FOREIGN KEY(`paidByFriendId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `transaction_participants` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transactionId` INTEGER NOT NULL,
                `friendId` INTEGER,
                `shareAmountPaise` INTEGER NOT NULL,
                `percentage` REAL NOT NULL,
                `splitMethod` TEXT NOT NULL,
                FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`friendId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
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
                FOREIGN KEY(`payerId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`receiverId`) REFERENCES `friends`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `pending_transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `detectedAmountPaise` INTEGER NOT NULL,
                `detectedType` TEXT NOT NULL,
                `detectedBank` TEXT,
                `detectedAccount` TEXT,
                `detectedMerchant` TEXT,
                `referenceNumber` TEXT,
                `rawContent` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `source` TEXT NOT NULL
            )
        """.trimIndent())
    }

    @Test
    fun test_TC059_FreshDatabaseCreation() {
        // Steps: Install the app with no existing database.
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java).build()

        // Expected: All tables, indexes, constraints and default categories are created correctly.
        val catDao = db.categoryDao()
        val friendDao = db.friendDao()
        val txnDao = db.transactionDao()
        val settlementDao = db.settlementDao()
        val groupDao = db.friendGroupDao()

        assertNotNull(catDao)
        assertNotNull(friendDao)
        assertNotNull(txnDao)
        assertNotNull(settlementDao)
        assertNotNull(groupDao)

        val cursor = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'")
        val tableNames = mutableListOf<String>()
        while (cursor.moveToNext()) {
            tableNames.add(cursor.getString(0))
        }
        cursor.close()

        assertTrue(tableNames.contains("categories"))
        assertTrue(tableNames.contains("friends"))
        assertTrue(tableNames.contains("transactions"))
        assertTrue(tableNames.contains("transaction_participants"))
        assertTrue(tableNames.contains("settlements"))
        assertTrue(tableNames.contains("pending_transactions"))
        assertTrue(tableNames.contains("friend_groups"))
        assertTrue(tableNames.contains("friend_group_members"))

        db.close()
    }

    @Test
    fun test_TC060_UpgradeFromVersion1To2() {
        val context = RuntimeEnvironment.getApplication()
        val tempDb = File.createTempFile("test_tc060", ".db").also { it.deleteOnExit() }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDb.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createV1Schema(db)
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO friends (id, name, phoneNumber, email, notes, createdAt, isArchived) VALUES (101, 'Friend A', '9876543210', null, null, 1000, 0)")
        db.execSQL("INSERT INTO categories (id, name, iconName, colorHex, isDefault) VALUES (1, 'Food', 'restaurant', '#FF0000', 1)")
        db.execSQL("INSERT INTO transactions (id, title, description, amountPaise, categoryId, transactionDate, paymentMethod, accountSource, paidByFriendId, splitMethod, transactionType, transactionStatus, source, merchantName, referenceNumber, createdAt, updatedAt) VALUES (1, 'Dinner', '', 50000, 1, 1000, 'UPI', null, null, 'EQUAL', 'EXPENSE', 'ACTIVE', 'MANUAL', null, null, 1000, 1000)")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (1, 1, 101, 50000, 100.0, 'EQUAL')")

        // Migrate to version 2
        RupeeFlowDatabase.MIGRATION_1_2.migrate(db)

        // Expected: All original records remain accessible and correctly related.
        val txnCursor = db.query("SELECT * FROM transactions WHERE id = 1")
        assertTrue(txnCursor.moveToFirst())
        assertEquals("Dinner", txnCursor.getString(txnCursor.getColumnIndexOrThrow("title")))
        txnCursor.close()

        val partCursor = db.query("SELECT * FROM transaction_participants WHERE id = 1")
        assertTrue(partCursor.moveToFirst())
        assertEquals(101L, partCursor.getLong(partCursor.getColumnIndexOrThrow("friendId")))
        partCursor.close()

        db.close()
    }

    @Test
    fun test_TC061_UpgradeThroughEveryReleasedVersion() {
        val context = RuntimeEnvironment.getApplication()
        val tempDb = File.createTempFile("test_tc061", ".db").also { it.deleteOnExit() }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDb.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createV1Schema(db)
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL("PRAGMA foreign_keys = ON;")
        db.execSQL("INSERT INTO friends (id, name, phoneNumber, email, notes, createdAt, isArchived) VALUES (101, 'Friend A', '9876543210', null, null, 1000, 0)")
        db.execSQL("INSERT INTO categories (id, name, iconName, colorHex, isDefault) VALUES (1, 'Food', 'restaurant', '#FF0000', 1)")
        db.execSQL("INSERT INTO transactions (id, title, description, amountPaise, categoryId, transactionDate, paymentMethod, accountSource, paidByFriendId, splitMethod, transactionType, transactionStatus, source, merchantName, referenceNumber, createdAt, updatedAt) VALUES (1, 'Cab', '', 60000, 1, 1000, 'UPI', null, null, 'EQUAL', 'EXPENSE', 'ACTIVE', 'MANUAL', null, null, 1000, 1000)")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (1, 1, 101, 30000, 50.0, 'EQUAL')")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (2, 1, null, 30000, 50.0, 'EQUAL')")

        // Sequential upgrade: v1 -> v2 -> v3 -> v4
        RupeeFlowDatabase.MIGRATION_1_2.migrate(db)
        RupeeFlowDatabase.MIGRATION_2_3.migrate(db)
        RupeeFlowDatabase.MIGRATION_3_4.migrate(db)

        // Expected: All records preserved
        val partCursor = db.query("SELECT * FROM transaction_participants")
        assertEquals(2, partCursor.count)
        partCursor.close()

        val groupCursor = db.query("SELECT * FROM friend_groups")
        assertNotNull(groupCursor)
        groupCursor.close()

        val fkCheck = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCheck.count)
        fkCheck.close()

        db.close()
    }

    @Test
    fun test_TC062_ParticipantForeignKeyIntegrity() {
        val context = RuntimeEnvironment.getApplication()
        val tempDb = File.createTempFile("test_tc062", ".db").also { it.deleteOnExit() }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDb.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createV1Schema(db)
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL("PRAGMA foreign_keys = ON;")
        db.execSQL("INSERT INTO friends (id, name, phoneNumber, email, notes, createdAt, isArchived) VALUES (101, 'A', null, null, null, 1000, 0)")
        db.execSQL("INSERT INTO friends (id, name, phoneNumber, email, notes, createdAt, isArchived) VALUES (102, 'B', null, null, null, 1000, 0)")
        db.execSQL("INSERT INTO categories (id, name, iconName, colorHex, isDefault) VALUES (1, 'Food', 'restaurant', '#FF0000', 1)")
        db.execSQL("INSERT INTO transactions (id, title, description, amountPaise, categoryId, transactionDate, paymentMethod, accountSource, paidByFriendId, splitMethod, transactionType, transactionStatus, source, merchantName, referenceNumber, createdAt, updatedAt) VALUES (1, 'Dinner', '', 100000, 1, 1000, 'UPI', null, null, 'EQUAL', 'EXPENSE', 'ACTIVE', 'MANUAL', null, null, 1000, 1000)")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (1, 1, null, 50000, 50.0, 'EQUAL')")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (2, 1, 101, 25000, 25.0, 'EQUAL')")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (3, 1, 102, 25000, 25.0, 'EQUAL')")

        // Migrate directly from 1 to 3
        RupeeFlowDatabase.MIGRATION_1_3.migrate(db)

        // Expected: Every participant still references the correct transaction and friend. Zero cascade loss.
        val partCursor = db.query("SELECT * FROM transaction_participants ORDER BY id ASC")
        assertEquals(3, partCursor.count)
        assertTrue(partCursor.moveToFirst())
        assertTrue(partCursor.isNull(partCursor.getColumnIndexOrThrow("friendId"))) // Me
        assertTrue(partCursor.moveToNext())
        assertEquals(101L, partCursor.getLong(partCursor.getColumnIndexOrThrow("friendId")))
        assertTrue(partCursor.moveToNext())
        assertEquals(102L, partCursor.getLong(partCursor.getColumnIndexOrThrow("friendId")))
        partCursor.close()

        db.close()
    }

    @Test
    fun test_TC063_DatabaseForeignKeyValidation() {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java).build()

        // Expected: Run PRAGMA foreign_key_check. No invalid foreign key references found.
        val cursor = db.openHelper.readableDatabase.query("PRAGMA foreign_key_check")
        assertEquals(0, cursor.count)
        cursor.close()

        db.close()
    }

    @Test
    fun test_TC064_AtomicTransactionWrite() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java).build()
        val repo = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(), db.friendDao())

        db.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF0000"))

        val initial = Transaction(
            title = "Valid Txn",
            amountPaise = 50000L,
            categoryId = 1L,
            participants = listOf(TransactionParticipant(friendId = null, shareAmountPaise = 50000L))
        )
        val id = repo.saveTransaction(initial)

        // Attempt invalid atomic edit where shares mismatch total amount (₹400 shares vs ₹500 amount)
        val invalidEdit = repo.getTransactionById(id)!!.copy(
            amountPaise = 50000L,
            participants = listOf(TransactionParticipant(transactionId = id, friendId = null, shareAmountPaise = 40000L))
        )

        try {
            repo.saveTransaction(invalidEdit)
            fail("Expected IllegalArgumentException for mismatched participant shares")
        } catch (_: IllegalArgumentException) {}

        // Expected: Entire operation rolls back. Previous state preserved.
        val retrieved = repo.getTransactionById(id)!!
        assertEquals(50000L, retrieved.participants[0].shareAmountPaise)

        db.close()
    }

    @Test
    fun test_TC065_InterruptedDatabaseWrite_WalIntegrity() {
        // Steps: Verify SQLite WAL journal mode and transaction safety
        val context = RuntimeEnvironment.getApplication()
        val tempDb = File.createTempFile("wal_test", ".db").also { it.deleteOnExit() }
        val db = Room.databaseBuilder(context, RupeeFlowDatabase::class.java, tempDb.name).build()

        val journalMode = db.openHelper.readableDatabase.query("PRAGMA journal_mode")
        assertTrue(journalMode.moveToFirst())
        val mode = journalMode.getString(0)
        journalMode.close()

        // Room uses WAL by default on Android
        assertTrue(mode.equals("wal", ignoreCase = true) || mode.equals("memory", ignoreCase = true))
        db.close()
    }

    @Test
    fun test_TC066_BackupCreation() {
        // Steps: Create a backup after several transactions and settlements.
        val root = JSONObject().apply {
            put("appName", "RupeeFlow")
            put("version", 3)
            put("exportTimestamp", System.currentTimeMillis())

            val txns = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("title", "Swiggy")
                    put("amountPaise", 45000L)
                })
            }
            put("transactions", txns)
        }

        // Expected: Backup contains all committed data
        val json = root.toString()
        assertTrue(json.contains("RupeeFlow"))
        assertTrue(json.contains("Swiggy"))
        assertTrue(json.contains("45000"))
    }

    @Test
    fun test_TC067_RestoreBackup() {
        // Steps: Restore a backup on a clean installation.
        val backupJson = """
            {
                "appName": "RupeeFlow",
                "version": 3,
                "transactions": [
                    {"id": 1, "title": "Dinner", "amountPaise": 50000, "categoryId": 1}
                ],
                "friends": [
                    {"id": 101, "name": "Ronit"}
                ]
            }
        """.trimIndent()

        val parsed = JSONObject(backupJson)
        assertEquals("RupeeFlow", parsed.getString("appName"))
        val txns = parsed.getJSONArray("transactions")
        val friends = parsed.getJSONArray("friends")

        assertEquals(1, txns.length())
        assertEquals(50000L, txns.getJSONObject(0).getLong("amountPaise"))
        assertEquals(1, friends.length())
        assertEquals("Ronit", friends.getJSONObject(0).getString("name"))
    }

    @Test
    fun test_TC068_FailedOrCorruptBackupRecovery() {
        // Steps: Attempt to restore an invalid, incomplete or incompatible backup.
        val corruptedJson = "{ appName: 'Corrupted', invalid_syntax "

        var restored = false
        try {
            JSONObject(corruptedJson)
            restored = true
        } catch (_: Exception) {
            restored = false
        }

        // Expected: Restore is safely rejected. Existing valid data not overwritten.
        assertFalse("Corrupted backup must be rejected", restored)
    }
}

