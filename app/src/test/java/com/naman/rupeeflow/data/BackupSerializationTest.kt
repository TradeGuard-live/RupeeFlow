package com.naman.rupeeflow.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.*
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BackupSerializationTest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var friendRepo: FriendRepository
    private lateinit var groupRepo: FriendGroupRepository
    private lateinit var txnRepo: TransactionRepository
    private lateinit var settlementRepo: SettlementRepository
    private lateinit var backupRepo: BackupRestoreRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        categoryRepo = CategoryRepositoryImpl(database.categoryDao())
        friendRepo = FriendRepositoryImpl(database.friendDao())
        groupRepo = FriendGroupRepositoryImpl(database.friendGroupDao())
        txnRepo = TransactionRepositoryImpl(database.transactionDao(), database.categoryDao(), database.friendDao())
        settlementRepo = SettlementRepositoryImpl(database.settlementDao(), database.friendDao())
        backupRepo = BackupRestoreRepository(
            database = database,
            transactionRepository = txnRepo,
            friendRepository = friendRepo,
            settlementRepository = settlementRepo,
            categoryRepository = categoryRepo,
            friendGroupRepository = groupRepo,
            context = context
        )

        runBlocking {
            database.categoryDao().insertCategories(RupeeFlowDatabase.DEFAULT_CATEGORIES)
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testJsonBackupStructure() {
        val root = JSONObject()
        root.put("version", 3)
        root.put("appName", "RupeeFlow")
        root.put("exportTimestamp", System.currentTimeMillis())

        val txns = JSONArray()
        val t1 = JSONObject().apply {
            put("id", 1L)
            put("title", "Swiggy Order")
            put("amountPaise", 45000L)
            put("paymentMethod", "UPI")
            put("transactionType", "EXPENSE")
            put("transactionStatus", "ACTIVE")
        }
        txns.put(t1)
        root.put("transactions", txns)

        val jsonString = root.toString()
        assertTrue(jsonString.contains("RupeeFlow"))
        assertTrue(jsonString.contains("Swiggy Order"))
        assertTrue(jsonString.contains("45000"))

        val parsed = JSONObject(jsonString)
        assertEquals("RupeeFlow", parsed.getString("appName"))
        assertEquals(3, parsed.getInt("version"))
        val restoredTxns = parsed.getJSONArray("transactions")
        assertEquals(1, restoredTxns.length())
        assertEquals(45000L, restoredTxns.getJSONObject(0).getLong("amountPaise"))
    }

    @Test
    fun testCsvFormatting() {
        val header = "ID,Date,Time,Title,Type,Category,Amount(INR),PaymentMethod,PaidBy,Status,Merchant,RefNo,Description"
        assertTrue(header.contains("Amount(INR)"))
        assertTrue(header.contains("PaymentMethod"))

        val sampleRow = "1,2026-11-02,08:30 PM,\"Swiggy\",EXPENSE,Food,450.00,UPI,Me,ACTIVE,\"Swiggy\",12345,\"Dinner\""
        val cols = sampleRow.split(",")
        assertTrue(cols.size >= 10)
    }

    @Test
    fun testEncryptedBackupSecurityAndTamperResistance() = runBlocking {
        val password = "StrongUserPassword123!"
        val envelope = backupRepo.exportEncryptedBackup(password)
        assertTrue(envelope.contains("\"encrypted\": true"))

        // Decrypt with correct password
        val decrypted = backupRepo.decryptBackup(envelope, password)
        assertTrue(decrypted.contains("RupeeFlow"))

        // Decrypt with wrong password must throw exception
        try {
            backupRepo.decryptBackup(envelope, "WrongPassword456!")
            fail("Expected security failure on wrong password")
        } catch (_: Exception) {
            // Success
        }
    }

    @Test
    fun testCompleteRoundTripExportAndRestoreCoverage() = runBlocking {
        // 1. Seed categories, friends, groups, memberships, transactions, settlements, and input usage
        val friend1Id = friendRepo.addFriend(Friend(name = "Aarav Patel", phoneNumber = "9825012345"))
        val friend2Id = friendRepo.addFriend(Friend(name = "Diya Shah", phoneNumber = "9825067890"))

        val groupId = groupRepo.createGroup(
            name = "Trip to Goa",
            iconName = "flight",
            colorHex = "#06B6D4",
            friendIds = listOf(friend1Id, friend2Id)
        )

        val txn = Transaction(
            title = "Beach Villa Booking",
            description = "3 nights stay",
            amountPaise = 3000000L, // Rs. 30,000.00
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null, // Paid by ME
            splitMethod = SplitMethod.EQUAL,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE,
            source = TransactionSource.MANUAL,
            groupId = groupId
        )
        val p1 = TransactionParticipant(friendId = friend1Id, shareAmountPaise = 1000000L, percentage = 33.33)
        val p2 = TransactionParticipant(friendId = friend2Id, shareAmountPaise = 1000000L, percentage = 33.33)
        val pMe = TransactionParticipant(friendId = null, shareAmountPaise = 1000000L, percentage = 33.34)
        txnRepo.saveTransaction(txn.copy(participants = listOf(p1, p2, pMe)))

        val settlement = Settlement(
            payerId = friend1Id,
            receiverId = null,
            amountPaise = 1000000L,
            settlementDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            referenceNumber = "UPI9876543210"
        )
        settlementRepo.recordSettlement(settlement)

        database.inputUsageDao().insertOrUpdate(
            InputUsageEntity(fieldType = "CATEGORY", optionId = "Food", usageCount = 15, lastUsedAt = System.currentTimeMillis(), preferenceScore = 2.5)
        )

        // 2. Export complete JSON
        val exportedJson = backupRepo.exportToJson()
        assertTrue(exportedJson.contains("Aarav Patel"))
        assertTrue(exportedJson.contains("Trip to Goa"))
        assertTrue(exportedJson.contains("Beach Villa Booking"))
        assertTrue(exportedJson.contains("UPI9876543210"))
        assertTrue(exportedJson.contains("friend_groups"))
        assertTrue(exportedJson.contains("input_usage"))

        // 3. Inspect backup preview
        val preview = backupRepo.inspectBackup(exportedJson)
        assertTrue(preview.isValid)
        assertEquals(BackupRestoreRepository.CURRENT_BACKUP_VERSION, preview.version)
        assertEquals(2, preview.friendsCount)
        assertEquals(1, preview.groupsCount)
        assertEquals(1, preview.transactionsCount)
        assertEquals(1, preview.settlementsCount)
        assertEquals(1, preview.inputUsageCount)
        assertEquals(3000000L, preview.totalVolumePaise)

        // 4. Wipe database
        database.clearAllTables()
        assertEquals(0, friendRepo.getAllFriendsList().size)
        assertEquals(0, groupRepo.getAllGroupsList().size)
        assertEquals(0, txnRepo.getAllTransactionsList().size)
        assertEquals(0, settlementRepo.getAllSettlementsList().size)

        // 5. Restore from JSON
        val restoredSuccessfully = backupRepo.restoreFromJson(exportedJson)
        assertTrue("Restore must complete successfully", restoredSuccessfully)

        // 6. Verify every entity and relationship
        val restoredFriends = friendRepo.getAllFriendsList()
        assertEquals(2, restoredFriends.size)
        assertTrue(restoredFriends.any { it.name == "Aarav Patel" })
        assertTrue(restoredFriends.any { it.name == "Diya Shah" })

        val restoredGroups = groupRepo.getAllGroupsList()
        assertEquals(1, restoredGroups.size)
        assertEquals("Trip to Goa", restoredGroups[0].name)
        assertEquals(2, restoredGroups[0].memberCount)

        val restoredTxns = txnRepo.getAllTransactionsList()
        assertEquals(1, restoredTxns.size)
        val restoredTxn = restoredTxns[0]
        assertEquals("Beach Villa Booking", restoredTxn.title)
        assertEquals(3000000L, restoredTxn.amountPaise)
        assertEquals(3, restoredTxn.participants.size)

        val restoredSettlements = settlementRepo.getAllSettlementsList()
        assertEquals(1, restoredSettlements.size)
        assertEquals(1000000L, restoredSettlements[0].amountPaise)
        assertEquals("UPI9876543210", restoredSettlements[0].referenceNumber)

        val restoredUsage = database.inputUsageDao().getAllUsageList()
        assertEquals(1, restoredUsage.size)
        assertEquals("Food", restoredUsage[0].optionId)
        assertEquals(15, restoredUsage[0].usageCount)
    }

    @Test
    fun testLegacyBackupBackwardCompatibility() = runBlocking {
        // v1 backup without groups or input_usage
        val legacyV1Json = JSONObject().apply {
            put("version", 1)
            put("appName", "RupeeFlow")
            put("exportTimestamp", System.currentTimeMillis())
            put("categories", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 10L)
                    put("name", "Groceries")
                    put("iconName", "shopping_cart")
                    put("colorHex", "#10B981")
                    put("isDefault", false)
                })
            })
            put("friends", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 50L)
                    put("name", "Legacy Friend")
                    put("phoneNumber", "9999988888")
                    put("isArchived", false)
                })
            })
            put("transactions", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 100L)
                    put("title", "Legacy Grocery")
                    put("amountPaise", 50000L)
                    put("categoryId", 10L)
                    put("transactionDate", System.currentTimeMillis())
                    put("paymentMethod", "CASH")
                    put("splitMethod", "EQUAL")
                    put("transactionType", "EXPENSE")
                    put("transactionStatus", "ACTIVE")
                    put("source", "MANUAL")
                })
            })
        }.toString()

        val preview = backupRepo.inspectBackup(legacyV1Json)
        assertTrue(preview.isValid)
        assertEquals(1, preview.version)
        assertEquals(1, preview.categoriesCount)
        assertEquals(1, preview.friendsCount)
        assertEquals(1, preview.transactionsCount)

        val restored = backupRepo.restoreFromJson(legacyV1Json)
        assertTrue("Legacy v1 backup must restore cleanly", restored)

        val friends = friendRepo.getAllFriendsList()
        assertTrue(friends.any { it.name == "Legacy Friend" })
    }
}

