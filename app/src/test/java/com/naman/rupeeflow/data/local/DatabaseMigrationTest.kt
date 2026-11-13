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

    @Test
    fun testMigration3To4_CreatesGroupTablesAndPreservesAllExistingRecords() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_3_4", ".db")
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

        // Migrate to version 3 first
        RupeeFlowDatabase.MIGRATION_1_3.migrate(db)

        // Migrate from version 3 to version 4
        RupeeFlowDatabase.MIGRATION_3_4.migrate(db)

        // 1. Verify ALL existing records survived migration completely intact
        verifyMigratedData(db)

        // 2. Verify friend_groups and friend_group_members tables exist and operate correctly
        db.execSQL("INSERT INTO friend_groups (id, name, iconName, colorHex, createdAt, updatedAt) VALUES (1, 'Trip Gang', 'flight', '#06B6D4', 4000, 4000)")
        db.execSQL("INSERT INTO friend_group_members (groupId, friendId, addedAt) VALUES (1, 1, 4000)")
        db.execSQL("INSERT INTO friend_group_members (groupId, friendId, addedAt) VALUES (1, 2, 4000)")

        val groupCursor = db.query("SELECT * FROM friend_groups WHERE id = 1")
        assertTrue(groupCursor.moveToFirst())
        assertEquals("Trip Gang", groupCursor.getString(groupCursor.getColumnIndexOrThrow("name")))
        groupCursor.close()

        val memberCursor = db.query("SELECT * FROM friend_group_members WHERE groupId = 1")
        assertEquals(2, memberCursor.count)
        memberCursor.close()

        // 3. Test CASCADE behavior on group deletion:
        // Deleting the group removes group members, but MUST NEVER delete friends or transactions!
        db.execSQL("DELETE FROM friend_groups WHERE id = 1")

        val deletedMemberCursor = db.query("SELECT * FROM friend_group_members WHERE groupId = 1")
        assertEquals(0, deletedMemberCursor.count)
        deletedMemberCursor.close()

        // Friends 1 & 2 must remain completely intact
        val friendCheck = db.query("SELECT id FROM friends WHERE id IN (1, 2)")
        assertEquals(2, friendCheck.count)
        friendCheck.close()

        // Foreign keys must remain completely valid
        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        db.close()
    }

    @Test
    fun testMigration1To4_DirectMigrationPreservesAllExistingRecords() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_1_4", ".db")
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

        // Direct migration from version 1 to 4
        RupeeFlowDatabase.MIGRATION_1_4.migrate(db)

        // Verify ALL records survived
        verifyMigratedData(db)

        // Verify new tables are functional
        db.execSQL("INSERT INTO friend_groups (id, name, createdAt, updatedAt) VALUES (1, 'College', 5000, 5000)")
        db.execSQL("INSERT INTO friend_group_members (groupId, friendId, addedAt) VALUES (1, 1, 5000)")

        val groupCursor = db.query("SELECT COUNT(*) FROM friend_groups")
        assertTrue(groupCursor.moveToFirst())
        assertEquals(1, groupCursor.getInt(0))
        groupCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        db.close()
    }

    @Test
    fun testMigration2To4_DirectMigrationPreservesAllExistingRecords() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_2_4", ".db")
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

        // Direct migration from version 2 to 4
        RupeeFlowDatabase.MIGRATION_2_4.migrate(db)

        // Verify ALL records survived
        verifyMigratedData(db)

        // Verify new tables are functional
        db.execSQL("INSERT INTO friend_groups (id, name, createdAt, updatedAt) VALUES (1, 'Hostel', 6000, 6000)")
        db.execSQL("INSERT INTO friend_group_members (groupId, friendId, addedAt) VALUES (1, 1, 6000)")

        val groupCursor = db.query("SELECT COUNT(*) FROM friend_groups")
        assertTrue(groupCursor.moveToFirst())
        assertEquals(1, groupCursor.getInt(0))
        groupCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        db.close()
    }

    @Test
    fun testMigration4To5_AddsGroupAndBillImageAndInputUsage() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_4_5", ".db")
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

        // Step up to version 4
        RupeeFlowDatabase.MIGRATION_1_2.migrate(db)
        RupeeFlowDatabase.MIGRATION_2_3.migrate(db)
        RupeeFlowDatabase.MIGRATION_3_4.migrate(db)

        // Now migrate 4 -> 5
        RupeeFlowDatabase.MIGRATION_4_5.migrate(db)

        // Verify data preserved
        verifyMigratedData(db)

        // Verify new columns and input_usage table
        db.execSQL("UPDATE friends SET profileImagePath = '/data/profiles/friend1.jpg' WHERE id = 1")
        val friendCursor = db.query("SELECT profileImagePath FROM friends WHERE id = 1")
        assertTrue(friendCursor.moveToFirst())
        assertEquals("/data/profiles/friend1.jpg", friendCursor.getString(0))
        friendCursor.close()

        db.execSQL("INSERT INTO friend_groups (id, name, createdAt, updatedAt, groupImagePath) VALUES (10, 'Trip', 7000, 7000, '/data/groups/trip.jpg')")
        val grpCursor = db.query("SELECT groupImagePath FROM friend_groups WHERE id = 10")
        assertTrue(grpCursor.moveToFirst())
        assertEquals("/data/groups/trip.jpg", grpCursor.getString(0))
        grpCursor.close()

        db.execSQL("UPDATE transactions SET groupId = 10, billImagePath = '/data/bills/bill1.jpg' WHERE id = 1")
        val txnCursor = db.query("SELECT groupId, billImagePath FROM transactions WHERE id = 1")
        assertTrue(txnCursor.moveToFirst())
        assertEquals(10L, txnCursor.getLong(0))
        assertEquals("/data/bills/bill1.jpg", txnCursor.getString(1))
        txnCursor.close()

        // Verify input_usage table
        db.execSQL("INSERT INTO input_usage (fieldType, optionId, usageCount, lastUsedAt, preferenceScore) VALUES ('CATEGORY', '1', 5, 10000, 0.0)")
        val usageCursor = db.query("SELECT usageCount FROM input_usage WHERE fieldType = 'CATEGORY' AND optionId = '1'")
        assertTrue(usageCursor.moveToFirst())
        assertEquals(5, usageCursor.getInt(0))
        usageCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        db.close()
    }

    @Test
    fun testMigration1To5_DirectMigrationPreservesAllExistingRecords() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_1_5", ".db")
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

        // Direct migration from version 1 to 5
        RupeeFlowDatabase.MIGRATION_1_5.migrate(db)

        verifyMigratedData(db)

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        db.close()
    }

    @Test
    fun testMigration5To6_AddsLocationColumnsToTransactionsAndPendingTransactions() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_5_6", ".db")
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

        // Step up to version 5
        RupeeFlowDatabase.MIGRATION_1_5.migrate(db)

        // Now migrate 5 -> 6
        RupeeFlowDatabase.MIGRATION_5_6.migrate(db)

        // Verify existing data preserved
        verifyMigratedData(db)

        // Verify location columns in transactions
        db.execSQL("""
            UPDATE transactions 
            SET latitude = 28.6139, 
                longitude = 77.2090, 
                accuracyMeters = 12.5, 
                locationCapturedAt = 1731302400000, 
                locationSource = 'DEVICE_CURRENT', 
                locationLabel = 'Connaught Place', 
                googleMapsUrl = 'https://maps.google.com/?q=28.6139,77.2090' 
            WHERE id = 1
        """.trimIndent())

        val txnCursor = db.query("SELECT latitude, longitude, accuracyMeters, locationSource, locationLabel FROM transactions WHERE id = 1")
        assertTrue(txnCursor.moveToFirst())
        assertEquals(28.6139, txnCursor.getDouble(0), 0.0001)
        assertEquals(77.2090, txnCursor.getDouble(1), 0.0001)
        assertEquals(12.5f, txnCursor.getFloat(2), 0.01f)
        assertEquals("DEVICE_CURRENT", txnCursor.getString(3))
        assertEquals("Connaught Place", txnCursor.getString(4))
        txnCursor.close()

        // Verify location columns in pending_transactions
        db.execSQL("""
            UPDATE pending_transactions 
            SET latitude = 12.9716, 
                longitude = 77.5946, 
                accuracyMeters = 8.0, 
                locationCapturedAt = 1731302400000, 
                locationSource = 'DEVICE_AT_DETECTION' 
            WHERE id = 1
        """.trimIndent())

        val pendingCursor = db.query("SELECT latitude, longitude, locationSource FROM pending_transactions WHERE id = 1")
        assertTrue(pendingCursor.moveToFirst())
        assertEquals(12.9716, pendingCursor.getDouble(0), 0.0001)
        assertEquals(77.5946, pendingCursor.getDouble(1), 0.0001)
        assertEquals("DEVICE_AT_DETECTION", pendingCursor.getString(2))
        pendingCursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        db.close()
    }

    @Test
    fun testMigration1To6_DirectMigrationPreservesAllExistingRecordsAndAddsLocations() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_1_6", ".db")
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

        // Direct migration from version 1 to 6
        RupeeFlowDatabase.MIGRATION_1_6.migrate(db)

        verifyMigratedData(db)

        // Verify newly added location columns work cleanly
        val cursor = db.query("SELECT latitude, longitude FROM transactions WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertTrue("Newly added latitude must default to null", cursor.isNull(0))
        assertTrue("Newly added longitude must default to null", cursor.isNull(1))
        cursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        db.close()
    }

    @Test
    fun testMigration6To7_AddsExpenseGroupsReportDraftsAndTransactionExpenseGroupColumns() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_6_7", ".db")
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

        // Migrate to version 6 first
        RupeeFlowDatabase.MIGRATION_1_6.migrate(db)

        // Now run MIGRATION_6_7
        RupeeFlowDatabase.MIGRATION_6_7.migrate(db)

        // Verify description added to friend_groups
        db.execSQL("INSERT INTO friend_groups (name, description, createdAt, updatedAt) VALUES ('Haveli', 'Close hostel friends', 1000, 1000)")
        val fgCursor = db.query("SELECT name, description FROM friend_groups WHERE name = 'Haveli'")
        assertTrue(fgCursor.moveToFirst())
        assertEquals("Haveli", fgCursor.getString(0))
        assertEquals("Close hostel friends", fgCursor.getString(1))
        fgCursor.close()

        // Verify expense_groups table
        db.execSQL("INSERT INTO expense_groups (name, description, colorHex, isArchived, createdAt, updatedAt) VALUES ('Goa Trip', 'Annual vacation 2026', '#3B82F6', 0, 1000, 1000)")
        val egCursor = db.query("SELECT id, name, description, isArchived FROM expense_groups WHERE name = 'Goa Trip'")
        assertTrue(egCursor.moveToFirst())
        val egId = egCursor.getLong(0)
        assertEquals("Goa Trip", egCursor.getString(1))
        assertEquals("Annual vacation 2026", egCursor.getString(2))
        assertEquals(0, egCursor.getInt(3))
        egCursor.close()

        // Verify transactions expenseGroupId and expenseGroupName columns
        db.execSQL("UPDATE transactions SET expenseGroupId = $egId, expenseGroupName = 'Goa Trip' WHERE id = 1")
        val txnCursor = db.query("SELECT expenseGroupId, expenseGroupName FROM transactions WHERE id = 1")
        assertTrue(txnCursor.moveToFirst())
        assertEquals(egId, txnCursor.getLong(0))
        assertEquals("Goa Trip", txnCursor.getString(1))
        txnCursor.close()

        // Verify report_drafts table
        db.execSQL("INSERT INTO report_drafts (name, periodType, startDate, endDate, excludedTransactionIdsJson, createdAt, updatedAt) VALUES ('Tax Statement', 'MONTHLY', 1000, 2000, '[1, 2]', 1000, 1000)")
        val draftCursor = db.query("SELECT name, periodType, excludedTransactionIdsJson FROM report_drafts WHERE name = 'Tax Statement'")
        assertTrue(draftCursor.moveToFirst())
        assertEquals("Tax Statement", draftCursor.getString(0))
        assertEquals("MONTHLY", draftCursor.getString(1))
        assertEquals("[1, 2]", draftCursor.getString(2))
        draftCursor.close()

        // Verify foreign key constraints are healthy
        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        db.close()
    }

    @Test
    fun testMigration1To7_DirectMigrationPreservesAllExistingRecordsAndCreatesVersion7Tables() {
        val context = RuntimeEnvironment.getApplication()
        val tempDbFile = File.createTempFile("test_migration_1_7", ".db")
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

        // Direct migration from version 1 to 7
        RupeeFlowDatabase.MIGRATION_1_7.migrate(db)

        verifyMigratedData(db)

        // Verify newly added version 7 columns in transactions
        val cursor = db.query("SELECT expenseGroupId, expenseGroupName FROM transactions WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertTrue("Newly added expenseGroupId must default to null", cursor.isNull(0))
        assertTrue("Newly added expenseGroupName must default to null", cursor.isNull(1))
        cursor.close()

        val fkCursor = db.query("PRAGMA foreign_key_check")
        assertEquals(0, fkCursor.count)
        fkCursor.close()

        db.close()
    }
}
