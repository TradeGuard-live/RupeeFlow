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

    @Test
    fun testMigration1To2_PreservesAllExistingRecords() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration", ".db")
        tempDbFile.deleteOnExit()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDbFile.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Create Version 1 schema
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

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = helper.writableDatabase

        // 1. Insert sample records into version 1 database
        db.execSQL("INSERT INTO categories (id, name, iconName, colorHex, isDefault) VALUES (1, 'Food', 'restaurant', '#FF0000', 1)")
        db.execSQL("INSERT INTO friends (id, name, phoneNumber, email, notes, createdAt, isArchived) VALUES (1, 'Ronit', '9876543210', 'ronit@test.com', 'Roommate', 1000, 0)")
        db.execSQL("""
            INSERT INTO transactions (id, title, description, amountPaise, categoryId, transactionDate, paymentMethod, accountSource, paidByFriendId, splitMethod, transactionType, transactionStatus, source, merchantName, referenceNumber, createdAt, updatedAt)
            VALUES (1, 'Dinner', 'Pizza night', 120000, 1, 1000, 'UPI', 'HDFC', 1, 'EQUAL', 'EXPENSE', 'ACTIVE', 'MANUAL', 'Dominos', 'REF123456', 1000, 1000)
        """.trimIndent())
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (1, 1, 1, 60000, 50.0, 'EQUAL')")
        db.execSQL("INSERT INTO transaction_participants (id, transactionId, friendId, shareAmountPaise, percentage, splitMethod) VALUES (2, 1, NULL, 60000, 50.0, 'EQUAL')")
        db.execSQL("""
            INSERT INTO settlements (id, payerId, receiverId, amountPaise, settlementDate, paymentMethod, referenceNumber, notes, isReversed, createdAt)
            VALUES (1, 1, NULL, 60000, 2000, 'UPI', 'SETTLE123', 'Settled dinner', 0, 2000)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO pending_transactions (id, detectedAmountPaise, detectedType, detectedBank, detectedAccount, detectedMerchant, referenceNumber, rawContent, timestamp, status, source)
            VALUES (1, 50000, 'EXPENSE', 'HDFC Bank', '1234', 'Swiggy', 'UPI999', 'Debited 500', 3000, 'PENDING', 'SMS')
        """.trimIndent())

        // 2. Execute migration from version 1 to version 2
        RupeeFlowDatabase.MIGRATION_1_2.migrate(db)

        // 3. Verify ALL records survived migration completely intact
        val catCursor = db.query("SELECT * FROM categories WHERE id = 1")
        assertTrue(catCursor.moveToFirst())
        assertEquals("Food", catCursor.getString(catCursor.getColumnIndexOrThrow("name")))
        catCursor.close()

        val friendCursor = db.query("SELECT * FROM friends WHERE id = 1")
        assertTrue(friendCursor.moveToFirst())
        assertEquals("Ronit", friendCursor.getString(friendCursor.getColumnIndexOrThrow("name")))
        assertEquals("9876543210", friendCursor.getString(friendCursor.getColumnIndexOrThrow("phoneNumber")))
        friendCursor.close()

        val txnCursor = db.query("SELECT * FROM transactions WHERE id = 1")
        assertTrue(txnCursor.moveToFirst())
        assertEquals("Dinner", txnCursor.getString(txnCursor.getColumnIndexOrThrow("title")))
        assertEquals(120000L, txnCursor.getLong(txnCursor.getColumnIndexOrThrow("amountPaise")))
        assertEquals(1L, txnCursor.getLong(txnCursor.getColumnIndexOrThrow("paidByFriendId")))
        txnCursor.close()

        val partCursor = db.query("SELECT * FROM transaction_participants ORDER BY id ASC")
        assertEquals(2, partCursor.count)
        assertTrue(partCursor.moveToFirst())
        assertEquals(1L, partCursor.getLong(partCursor.getColumnIndexOrThrow("friendId")))
        assertEquals(60000L, partCursor.getLong(partCursor.getColumnIndexOrThrow("shareAmountPaise")))
        assertTrue(partCursor.moveToNext())
        assertTrue(partCursor.isNull(partCursor.getColumnIndexOrThrow("friendId"))) // NULL = ME
        assertEquals(60000L, partCursor.getLong(partCursor.getColumnIndexOrThrow("shareAmountPaise")))
        partCursor.close()

        val setCursor = db.query("SELECT * FROM settlements WHERE id = 1")
        assertTrue(setCursor.moveToFirst())
        assertEquals(1L, setCursor.getLong(setCursor.getColumnIndexOrThrow("payerId")))
        assertTrue(setCursor.isNull(setCursor.getColumnIndexOrThrow("receiverId"))) // NULL = ME
        assertEquals(60000L, setCursor.getLong(setCursor.getColumnIndexOrThrow("amountPaise")))
        setCursor.close()

        val pendingCursor = db.query("SELECT * FROM pending_transactions WHERE id = 1")
        assertTrue(pendingCursor.moveToFirst())
        assertEquals(50000L, pendingCursor.getLong(pendingCursor.getColumnIndexOrThrow("detectedAmountPaise")))
        assertEquals("HDFC Bank", pendingCursor.getString(pendingCursor.getColumnIndexOrThrow("detectedBank")))
        pendingCursor.close()

        db.close()
    }
}
