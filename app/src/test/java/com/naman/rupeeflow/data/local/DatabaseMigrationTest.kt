package com.naman.rupeeflow.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationTest {

    private fun createV1Schema(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `iconName` TEXT NOT NULL,
                `colorHex` TEXT NOT NULL,
                `isDefault` INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `friends` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `phoneNumber` TEXT,
                `email` TEXT,
                `notes` TEXT,
                `createdAt` INTEGER NOT NULL,
                `isArchived` INTEGER NOT NULL
            )
        """.trimIndent())

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

    private fun insertTestData(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO categories (id, name, iconName, colorHex, isDefault) VALUES (1, 'Food', 'restaurant', '#FF0000', 1)")
        db.execSQL("INSERT INTO categories (id, name, iconName, colorHex, isDefault) VALUES (2, 'Travel', 'flight', '#0000FF', 0)")

        db.execSQL("INSERT INTO friends (id, name, phoneNumber, email, notes, createdAt, isArchived) VALUES (1, 'Ronit', '9876543210', 'ronit@test.com', 'Roommate', 1000, 0)")
        db.execSQL("INSERT INTO friends (id, name, phoneNumber, email, notes, createdAt, isArchived) VALUES (2, 'Priya', '9876543211', 'priya@test.com', 'Colleague', 1100, 0)")

        // Transaction 1: Shared dinner (Me paid, Ronit split)
        db.execSQL("""
            INSERT INTO transactions (id, title, description, amountPaise, categoryId, transactionDate, paymentMethod, accountSource, paidByFriendId, splitMethod, transactionType, transactionStatus, source, merchantName, referenceNumber, createdAt, updatedAt)
            VALUES (1, 'Dinner', 'Pizza night', 120000, 1, 1000, 'UPI', 'HDFC', NULL, 'EQUAL', 'EXPENSE', 'ACTIVE', 'MANUAL', 'Dominos', 'REF123456', 1000, 1000)
        """.trimIndent())
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (1, 1, 1, 60000, 50.0, 'EQUAL')")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (2, 1, NULL, 60000, 50.0, 'EQUAL')")

        // Transaction 2: Multi-friend trip (Priya paid, Ronit + Priya + Me split)
        db.execSQL("""
            INSERT INTO transactions (id, title, description, amountPaise, categoryId, transactionDate, paymentMethod, accountSource, paidByFriendId, splitMethod, transactionType, transactionStatus, source, merchantName, referenceNumber, createdAt, updatedAt)
            VALUES (2, 'Goa Trip', 'Cab fare', 300000, 2, 1500, 'UPI', 'ICICI', 2, 'EQUAL', 'EXPENSE', 'ACTIVE', 'MANUAL', 'Uber', 'REF789', 1500, 1500)
        """.trimIndent())
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (3, 2, 1, 100000, 33.33, 'EQUAL')")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (4, 2, 2, 100000, 33.33, 'EQUAL')")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (5, 2, NULL, 100000, 33.34, 'EQUAL')")

        // Transaction 3: Solo expense (no participants)
        db.execSQL("""
            INSERT INTO transactions (id, title, description, amountPaise, categoryId, transactionDate, paymentMethod, accountSource, paidByFriendId, splitMethod, transactionType, transactionStatus, source, merchantName, referenceNumber, createdAt, updatedAt)
            VALUES (3, 'Groceries', 'Supermarket', 45000, 1, 1800, 'CASH', NULL, NULL, 'EQUAL', 'EXPENSE', 'ACTIVE', 'MANUAL', 'Blinkit', NULL, 1800, 1800)
        """.trimIndent())

        // Settlements
        db.execSQL("""
            INSERT INTO settlements (id, payerId, receiverId, amountPaise, settlementDate, paymentMethod, referenceNumber, notes, isReversed, createdAt)
            VALUES (1, 1, NULL, 60000, 2000, 'UPI', 'SETTLE123', 'Settled dinner', 0, 2000)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO settlements (id, payerId, receiverId, amountPaise, settlementDate, paymentMethod, referenceNumber, notes, isReversed, createdAt)
            VALUES (2, NULL, 2, 100000, 2100, 'UPI', 'SETTLE456', 'Paid Priya', 0, 2100)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO settlements (id, payerId, receiverId, amountPaise, settlementDate, paymentMethod, referenceNumber, notes, isReversed, createdAt)
            VALUES (3, 2, NULL, 50000, 2200, 'CASH', NULL, 'Reversed test', 1, 2200)
        """.trimIndent())

        // Pending transaction
        db.execSQL("""
            INSERT INTO pending_transactions (id, detectedAmountPaise, detectedType, detectedBank, detectedAccount, detectedMerchant, referenceNumber, rawContent, timestamp, status, source)
            VALUES (1, 50000, 'EXPENSE', 'HDFC Bank', '1234', 'Swiggy', 'UPI999', 'Debited 500', 3000, 'PENDING', 'SMS')
        """.trimIndent())
    }

    private fun verifyMigratedData(db: SupportSQLiteDatabase) {
        val catCursor = db.query("SELECT * FROM categories ORDER BY id ASC")
        assertEquals(2, catCursor.count)
        assertTrue(catCursor.moveToFirst())
        assertEquals("Food", catCursor.getString(catCursor.getColumnIndexOrThrow("name")))
        assertTrue(catCursor.moveToNext())
        assertEquals("Travel", catCursor.getString(catCursor.getColumnIndexOrThrow("name")))
        catCursor.close()

        val friendCursor = db.query("SELECT * FROM friends ORDER BY id ASC")
        assertEquals(2, friendCursor.count)
        assertTrue(friendCursor.moveToFirst())
        assertEquals("Ronit", friendCursor.getString(friendCursor.getColumnIndexOrThrow("name")))
        assertEquals("9876543210", friendCursor.getString(friendCursor.getColumnIndexOrThrow("phoneNumber")))
        assertTrue(friendCursor.moveToNext())
        assertEquals("Priya", friendCursor.getString(friendCursor.getColumnIndexOrThrow("name")))
        friendCursor.close()

        val txnCursor = db.query("SELECT * FROM transactions ORDER BY id ASC")
        assertEquals(3, txnCursor.count)
        assertTrue(txnCursor.moveToFirst())
        assertEquals("Dinner", txnCursor.getString(txnCursor.getColumnIndexOrThrow("title")))
        assertEquals(120000L, txnCursor.getLong(txnCursor.getColumnIndexOrThrow("amountPaise")))
        assertTrue(txnCursor.isNull(txnCursor.getColumnIndexOrThrow("paidByFriendId"))) // NULL = ME

        assertTrue(txnCursor.moveToNext())
        assertEquals("Goa Trip", txnCursor.getString(txnCursor.getColumnIndexOrThrow("title")))
        assertEquals(300000L, txnCursor.getLong(txnCursor.getColumnIndexOrThrow("amountPaise")))
        assertEquals(2L, txnCursor.getLong(txnCursor.getColumnIndexOrThrow("paidByFriendId")))

        assertTrue(txnCursor.moveToNext())
        assertEquals("Groceries", txnCursor.getString(txnCursor.getColumnIndexOrThrow("title")))
        assertEquals(45000L, txnCursor.getLong(txnCursor.getColumnIndexOrThrow("amountPaise")))
        txnCursor.close()

        val partCursor = db.query("SELECT * FROM transaction_participants ORDER BY id ASC")
        assertEquals(5, partCursor.count) // ALL 5 participants MUST be preserved with zero loss
        assertTrue(partCursor.moveToFirst())
        assertEquals(1L, partCursor.getLong(partCursor.getColumnIndexOrThrow("transactionId")))
        assertEquals(1L, partCursor.getLong(partCursor.getColumnIndexOrThrow("friendId")))
        assertEquals(60000L, partCursor.getLong(partCursor.getColumnIndexOrThrow("shareAmountPaise")))

        assertTrue(partCursor.moveToNext())
        assertEquals(1L, partCursor.getLong(partCursor.getColumnIndexOrThrow("transactionId")))
        assertTrue(partCursor.isNull(partCursor.getColumnIndexOrThrow("friendId"))) // NULL = ME
        assertEquals(60000L, partCursor.getLong(partCursor.getColumnIndexOrThrow("shareAmountPaise")))

        assertTrue(partCursor.moveToNext())
        assertEquals(2L, partCursor.getLong(partCursor.getColumnIndexOrThrow("transactionId")))
        assertEquals(1L, partCursor.getLong(partCursor.getColumnIndexOrThrow("friendId")))
        assertEquals(100000L, partCursor.getLong(partCursor.getColumnIndexOrThrow("shareAmountPaise")))

        assertTrue(partCursor.moveToNext())
        assertEquals(2L, partCursor.getLong(partCursor.getColumnIndexOrThrow("transactionId")))
        assertEquals(2L, partCursor.getLong(partCursor.getColumnIndexOrThrow("friendId")))
        assertEquals(100000L, partCursor.getLong(partCursor.getColumnIndexOrThrow("shareAmountPaise")))

        assertTrue(partCursor.moveToNext())
        assertEquals(2L, partCursor.getLong(partCursor.getColumnIndexOrThrow("transactionId")))
        assertTrue(partCursor.isNull(partCursor.getColumnIndexOrThrow("friendId"))) // NULL = ME
        assertEquals(100000L, partCursor.getLong(partCursor.getColumnIndexOrThrow("shareAmountPaise")))
        partCursor.close()

        val setCursor = db.query("SELECT * FROM settlements ORDER BY id ASC")
        assertEquals(3, setCursor.count)
        assertTrue(setCursor.moveToFirst())
        assertEquals(1L, setCursor.getLong(setCursor.getColumnIndexOrThrow("payerId")))
        assertTrue(setCursor.isNull(setCursor.getColumnIndexOrThrow("receiverId")))
        assertEquals(60000L, setCursor.getLong(setCursor.getColumnIndexOrThrow("amountPaise")))
        assertEquals(0, setCursor.getInt(setCursor.getColumnIndexOrThrow("isReversed")))

        assertTrue(setCursor.moveToNext())
        assertTrue(setCursor.isNull(setCursor.getColumnIndexOrThrow("payerId")))
        assertEquals(2L, setCursor.getLong(setCursor.getColumnIndexOrThrow("receiverId")))
        assertEquals(100000L, setCursor.getLong(setCursor.getColumnIndexOrThrow("amountPaise")))
        assertEquals(0, setCursor.getInt(setCursor.getColumnIndexOrThrow("isReversed")))

        assertTrue(setCursor.moveToNext())
        assertEquals(2L, setCursor.getLong(setCursor.getColumnIndexOrThrow("payerId")))
        assertTrue(setCursor.isNull(setCursor.getColumnIndexOrThrow("receiverId")))
        assertEquals(50000L, setCursor.getLong(setCursor.getColumnIndexOrThrow("amountPaise")))
        assertEquals(1, setCursor.getInt(setCursor.getColumnIndexOrThrow("isReversed")))
        setCursor.close()

        val pendingCursor = db.query("SELECT * FROM pending_transactions WHERE id = 1")
        assertTrue(pendingCursor.moveToFirst())
        assertEquals(50000L, pendingCursor.getLong(pendingCursor.getColumnIndexOrThrow("detectedAmountPaise")))
        assertEquals("HDFC Bank", pendingCursor.getString(pendingCursor.getColumnIndexOrThrow("detectedBank")))
        pendingCursor.close()

        // Verify foreign key integrity check has zero violations
        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()
    }

    @Test
    fun testMigration1To2_PreservesAllExistingRecords() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_1_2", ".db")
        tempDbFile.deleteOnExit()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDbFile.absolutePath)
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

        insertTestData(db)

        // 2. Execute migration from version 1 to version 2
        RupeeFlowDatabase.MIGRATION_1_2.migrate(db)

        // 3. Verify ALL records survived migration completely intact
        verifyMigratedData(db)

        db.close()
    }

    @Test
    fun testMigration2To3_PreservesAllExistingRecords() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_2_3", ".db")
        tempDbFile.deleteOnExit()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDbFile.absolutePath)
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

        insertTestData(db)

        // Migrate to version 2 first
        RupeeFlowDatabase.MIGRATION_1_2.migrate(db)

        // Now migrate from version 2 to version 3
        RupeeFlowDatabase.MIGRATION_2_3.migrate(db)

        // Verify ALL records survived migration
        verifyMigratedData(db)

        db.close()
    }

    @Test
    fun testMigration1To3_PreservesAllExistingRecords() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_1_3", ".db")
        tempDbFile.deleteOnExit()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDbFile.absolutePath)
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

        insertTestData(db)

        // Direct migration from version 1 to version 3
        RupeeFlowDatabase.MIGRATION_1_3.migrate(db)

        // Verify ALL records survived migration
        verifyMigratedData(db)

        db.close()
    }
}
