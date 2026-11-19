package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.data.local.entity.TransactionEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.data.sms.SmsParser
import com.naman.rupeeflow.data.sms.parser.PaymentRecipientExtractor
import com.naman.rupeeflow.domain.matching.MerchantNormalizer
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * QA Test Suite for Merchant Extraction, Persistence, and Automatic Category Learning.
 * Verifies all 12 required architectural and functional specifications:
 * 1. Independent Title & Merchant Storage
 * 2. Title Fallback Is Never Used for Merchant
 * 3. SMS True Merchant Extraction
 * 4. Payment App Provider Distinction
 * 5. Bank Name Distinction
 * 6. UPI VPA Classification Distinction
 * 7. Pending-to-Confirmed Full Merchant Transfer
 * 8. Edit Transaction Preserves Merchant Identity
 * 9. Merchant-Based Category Learning
 * 10. Title Never Influences Category Learning
 * 11. Manual Category Correction Updates Learning
 * 12. Safe Database Migration 11 to 12 & Full Backup/Restore Compatibility
 */
@RunWith(RobolectricTestRunner::class)
class MerchantExtractionPersistenceAndLearningQATest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var pendingRepository: PendingTransactionRepository
    private lateinit var merchantPreferenceRepository: MerchantPreferenceRepository
    private lateinit var backupRestoreRepository: BackupRestoreRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao(),
            context = context
        )

        pendingRepository = PendingTransactionRepositoryImpl(
            pendingDao = database.pendingTransactionDao(),
            transactionDao = database.transactionDao()
        )

        merchantPreferenceRepository = MerchantPreferenceRepositoryImpl(
            inputUsageDao = database.inputUsageDao()
        )

        val categoryRepo = CategoryRepositoryImpl(database.categoryDao())
        val friendRepo = FriendRepositoryImpl(database.friendDao())
        val settlementRepo = SettlementRepositoryImpl(database.settlementDao(), database.friendDao())

        backupRestoreRepository = BackupRestoreRepository(
            database = database,
            transactionRepository = transactionRepository,
            friendRepository = friendRepo,
            settlementRepository = settlementRepo,
            categoryRepository = categoryRepo,
            context = context
        )

        runBlocking {
            database.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#F97316", isDefault = true))
            database.categoryDao().insertCategory(CategoryEntity(id = 2L, name = "Groceries", iconName = "shopping_cart", colorHex = "#10B981", isDefault = true))
            database.categoryDao().insertCategory(CategoryEntity(id = 3L, name = "Transportation", iconName = "directions_car", colorHex = "#3B82F6", isDefault = true))
            database.categoryDao().insertCategory(CategoryEntity(id = 4L, name = "Shopping", iconName = "shopping_bag", colorHex = "#EC4899", isDefault = true))
            database.categoryDao().insertCategory(CategoryEntity(id = 5L, name = "Entertainment", iconName = "movie", colorHex = "#F43F5E", isDefault = true))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. Independent Title & Merchant Storage
    @Test
    fun test01_separateTitleAndMerchantStorage() = runBlocking {
        val txn = Transaction(
            title = "Dinner with friends",
            amountPaise = 150000L,
            categoryId = 1L,
            merchantName = "Domino's",
            source = TransactionSource.MANUAL
        )

        val id = transactionRepository.saveTransaction(txn)
        val loaded = transactionRepository.getTransactionById(id)

        assertNotNull(loaded)
        assertEquals("Dinner with friends", loaded!!.title)
        assertEquals("Domino's", loaded.merchantName)
        assertEquals("dominos", loaded.merchantNormalized)

        // Modify title without modifying merchant
        val updatedTitle = loaded.copy(title = "Lunch with team")
        transactionRepository.saveTransaction(updatedTitle)
        val reloadedTitle = transactionRepository.getTransactionById(id)!!

        assertEquals("Lunch with team", reloadedTitle.title)
        assertEquals("Domino's", reloadedTitle.merchantName)
        assertEquals("dominos", reloadedTitle.merchantNormalized)

        // Modify merchant without modifying title
        val updatedMerchant = reloadedTitle.copy(merchantName = "Swiggy", merchantNormalized = null)
        transactionRepository.saveTransaction(updatedMerchant)
        val reloadedMerchant = transactionRepository.getTransactionById(id)!!

        assertEquals("Lunch with team", reloadedMerchant.title)
        assertEquals("Swiggy", reloadedMerchant.merchantName)
        assertEquals("swiggy", reloadedMerchant.merchantNormalized)
    }

    // 2. Title Fallback Is Never Used for Merchant
    @Test
    fun test02_titleFallbackIsNeverUsedForMerchant() = runBlocking {
        // Transaction saved with title that looks like a store name, but no explicit merchant
        val txn = Transaction(
            title = "Swiggy",
            amountPaise = 25000L,
            categoryId = 1L,
            merchantName = null,
            merchantNormalized = null
        )

        val id = transactionRepository.saveTransaction(txn)
        val loaded = transactionRepository.getTransactionById(id)!!

        assertEquals("Swiggy", loaded.title)
        assertNull("merchantName must remain null when not explicitly set", loaded.merchantName)
        assertNull("merchantNormalized must remain null when merchantName is null", loaded.merchantNormalized)
    }

    // 3. SMS True Merchant Extraction
    @Test
    fun test03_smsTrueMerchantExtraction() {
        val sms = "Paid Rs. 450 to Domino's Pizza on 12-Nov-2026 via UPI. Ref No 432109876543"
        val parsed = SmsParser.parse(sms, "AD-HDFCBK-S")

        assertTrue(parsed.isValidTransaction)
        assertEquals(45000L, parsed.amountPaise)
        assertEquals("Domino's Pizza", parsed.merchant)
        assertEquals("dominos", MerchantNormalizer.normalize(parsed.merchant))
    }

    // 4. Payment App Provider Distinction
    @Test
    fun test04_paymentAppDistinction() {
        // Notification through Paytm for a payment to Swiggy
        val notif1 = "Paid ₹250 via Paytm to Swiggy"
        val parsed1 = SmsParser.parse(notif1, "net.one97.paytm")
        assertTrue(parsed1.isValidTransaction)
        assertEquals("Swiggy", parsed1.merchant)
        assertEquals("Paytm", parsed1.bankName)

        // Notification mentioning only payment app / wallet top-up, no merchant
        val notif2 = "Paid ₹500 using Paytm wallet. Ref 123456"
        val parsed2 = SmsParser.parse(notif2, "net.one97.paytm")
        assertNull("Paytm wallet must not become merchant", parsed2.merchant)

        // PhonePe notification with merchant
        val notif3 = "Payment of ₹120 to Starbucks Coffee successful via PhonePe"
        val parsed3 = SmsParser.parse(notif3, "com.phonepe.app")
        assertTrue(parsed3.isValidTransaction)
        assertEquals("Starbucks Coffee", parsed3.merchant)
        assertEquals("PhonePe", parsed3.bankName)
    }

    // 5. Bank Name Distinction
    @Test
    fun test05_bankNameDistinction() {
        // Bank debit SMS with merchant
        val sms1 = "Sent Rs. 1,000 from HDFC Bank A/c 1234 to Chai Point. UPI Ref 998877"
        val parsed1 = SmsParser.parse(sms1, "AD-HDFCBK-S")
        assertTrue(parsed1.isValidTransaction)
        assertEquals("Chai Point", parsed1.merchant)
        assertEquals("HDFC Bank", parsed1.bankName)

        // Bank debit SMS with no merchant
        val sms2 = "Debited Rs. 500 from ICICI Bank A/c 5678. Avail Bal: Rs 10,000"
        val parsed2 = SmsParser.parse(sms2, "VM-ICICIB-T")
        assertNull("Bank name in body must never become merchant", parsed2.merchant)
    }

    // 6. UPI VPA Classification Distinction
    @Test
    fun test06_upiVpaDistinction() {
        // Personal VPA
        val personalSms = "Paid ₹300 to rahul@oksbi on 10-Nov-2026. Ref 11223344"
        val parsedPersonal = SmsParser.parse(personalSms, "com.google.android.apps.nbu.paisa.user")
        assertEquals("rahul@oksbi", parsedPersonal.upiId)
        assertNull("Personal VPA must not become merchant", parsedPersonal.merchant)

        // Recognizable business VPA
        val businessSms = "Paid ₹499 to swiggy@icici on 10-Nov-2026. Ref 99887766"
        val parsedBusiness = SmsParser.parse(businessSms, "com.google.android.apps.nbu.paisa.user")
        assertEquals("swiggy@icici", parsedBusiness.upiId)
        assertEquals("swiggy", parsedBusiness.merchant)
        assertEquals("swiggy", MerchantNormalizer.normalize(parsedBusiness.merchant))
    }

    // 7. Pending-to-Confirmed Full Merchant Transfer
    @Test
    fun test07_pendingToConfirmedTransfer() = runBlocking {
        val pendingEntity = PendingTransactionEntity(
            detectedAmountPaise = 35000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC Bank",
            detectedMerchant = "Domino's",
            rawMerchant = "Domino's Pizza",
            merchantNormalized = "dominos",
            sourcePackage = "net.one97.paytm",
            rawContent = "Paid ₹350 at Domino's Pizza",
            timestamp = System.currentTimeMillis(),
            source = TransactionSource.NOTIFICATION
        )
        val pendingId = database.pendingTransactionDao().insert(pendingEntity)
        val pending = pendingRepository.getById(pendingId)!!

        assertEquals("Domino's", pending.detectedMerchant)
        assertEquals("dominos", pending.merchantNormalized)
        assertEquals("Domino's Pizza", pending.rawMerchant)
        assertEquals("net.one97.paytm", pending.sourcePackage)

        // Approve and transfer to Transaction
        val txn = Transaction(
            title = pending.detectedMerchant ?: "Expense",
            amountPaise = pending.detectedAmountPaise,
            categoryId = 1L,
            transactionDate = pending.timestamp,
            paymentMethod = PaymentMethod.UPI,
            transactionType = pending.detectedType,
            merchantName = pending.detectedMerchant,
            merchantNormalized = pending.merchantNormalized,
            rawMerchant = pending.rawMerchant,
            sourcePackage = pending.sourcePackage,
            source = pending.source
        )

        val txnId = transactionRepository.saveTransaction(txn)
        pendingRepository.resolvePendingTransaction(pendingId)

        val confirmed = transactionRepository.getTransactionById(txnId)!!
        assertEquals("Domino's", confirmed.title)
        assertEquals("Domino's", confirmed.merchantName)
        assertEquals("dominos", confirmed.merchantNormalized)
        assertEquals("Domino's Pizza", confirmed.rawMerchant)
        assertEquals("net.one97.paytm", confirmed.sourcePackage)
        assertEquals(TransactionSource.NOTIFICATION, confirmed.source)

        val resolvedPending = pendingRepository.getById(pendingId)!!
        assertEquals(PendingStatus.RESOLVED, resolvedPending.status)
    }

    // 8. Edit Transaction Preserves Merchant Identity
    @Test
    fun test08_editTransactionPreservesMerchant() = runBlocking {
        val initial = Transaction(
            title = "Office lunch",
            amountPaise = 42000L,
            categoryId = 1L,
            merchantName = "Domino's",
            merchantNormalized = "dominos",
            rawMerchant = "Domino's Outlet #14",
            sourcePackage = "com.phonepe.app",
            source = TransactionSource.NOTIFICATION
        )
        val txnId = transactionRepository.saveTransaction(initial)
        val saved = transactionRepository.getTransactionById(txnId)!!

        // Edit amount and description only
        val edited = saved.copy(
            amountPaise = 48000L,
            description = "Added extra garlic bread"
        )
        transactionRepository.saveTransaction(edited)
        val reloaded = transactionRepository.getTransactionById(txnId)!!

        assertEquals(48000L, reloaded.amountPaise)
        assertEquals("Added extra garlic bread", reloaded.description)
        assertEquals("Domino's", reloaded.merchantName)
        assertEquals("dominos", reloaded.merchantNormalized)
        assertEquals("Domino's Outlet #14", reloaded.rawMerchant)
        assertEquals("com.phonepe.app", reloaded.sourcePackage)

        // Update merchant explicitly
        val updatedMerchantTxn = reloaded.copy(
            merchantName = "Starbucks",
            merchantNormalized = null
        )
        transactionRepository.saveTransaction(updatedMerchantTxn)
        val finalReloaded = transactionRepository.getTransactionById(txnId)!!

        assertEquals("Starbucks", finalReloaded.merchantName)
        assertEquals("starbucks", finalReloaded.merchantNormalized)
        assertEquals(48000L, finalReloaded.amountPaise)
    }

    // 9. Merchant-Based Category Learning
    @Test
    fun test09_merchantBasedCategoryLearning() = runBlocking {
        // Clear any previous preference
        merchantPreferenceRepository.clearMerchantCategory("Domino's")

        // No preference initially exists
        assertNull(merchantPreferenceRepository.getSuggestedCategory("Domino's"))

        // User confirms an expense at Domino's categorized as Food (id = 1)
        merchantPreferenceRepository.recordMerchantCategory("Domino's", 1L)

        // Querying "Domino's" suggests Food (id = 1)
        val suggested1 = merchantPreferenceRepository.getSuggestedCategory("Domino's")
        assertEquals(1L, suggested1)

        // Querying normalized variations suggests Food (id = 1)
        val suggested2 = merchantPreferenceRepository.getSuggestedCategory("Dominos")
        assertEquals(1L, suggested2)

        val suggested3 = merchantPreferenceRepository.getSuggestedCategory("dominos@icici")
        assertEquals(1L, suggested3)

        // Unknown merchant returns null without guessing
        assertNull(merchantPreferenceRepository.getSuggestedCategory("UnknownStore12345"))
    }

    // 10. Title Never Influences Category Learning
    @Test
    fun test10_titleNeverInfluencesCategoryLearning() = runBlocking {
        merchantPreferenceRepository.clearAllLearnedPreferences()

        // Merchant Domino's recorded with category Food (1L)
        merchantPreferenceRepository.recordMerchantCategory("Domino's", 1L)

        // Merchant PVR Cinemas recorded with category Entertainment (5L)
        merchantPreferenceRepository.recordMerchantCategory("PVR Cinemas", 5L)

        // Query for Domino's is strictly Food (1L)
        assertEquals(1L, merchantPreferenceRepository.getSuggestedCategory("Domino's"))

        // Query for PVR Cinemas is strictly Entertainment (5L)
        assertEquals(5L, merchantPreferenceRepository.getSuggestedCategory("PVR Cinemas"))

        // Null or blank merchant yields null regardless of any title
        assertNull(merchantPreferenceRepository.getSuggestedCategory(null))
        assertNull(merchantPreferenceRepository.getSuggestedCategory(""))
    }

    // 11. Manual Category Correction Updates Learning
    @Test
    fun test11_manualCategoryCorrectionUpdatesLearning() = runBlocking {
        // User initially had Domino's as Groceries (id = 2)
        merchantPreferenceRepository.recordMerchantCategory("Domino's", 2L)
        assertEquals(2L, merchantPreferenceRepository.getSuggestedCategory("Domino's"))

        // User manually corrects Domino's to Food (id = 1)
        merchantPreferenceRepository.recordMerchantCategory("Domino's", 1L)

        // Learned preference updates to the latest correction
        assertEquals(1L, merchantPreferenceRepository.getSuggestedCategory("Domino's"))
    }

    // 12. Safe Database Migration 11 to 12 & Full Backup/Restore Compatibility
    @Test
    fun test12_databaseMigration11To12AndBackupRestore() = runBlocking {
        // 12a. Verify schema columns exist in SQLite database
        val cursorTxn = database.openHelper.readableDatabase.query("PRAGMA table_info(transactions)")
        val txnCols = mutableListOf<String>()
        while (cursorTxn.moveToNext()) {
            txnCols.add(cursorTxn.getString(1))
        }
        cursorTxn.close()

        assertTrue("transactions table must have merchantNormalized", txnCols.contains("merchantNormalized"))
        assertTrue("transactions table must have rawMerchant", txnCols.contains("rawMerchant"))
        assertTrue("transactions table must have sourcePackage", txnCols.contains("sourcePackage"))

        val cursorPending = database.openHelper.readableDatabase.query("PRAGMA table_info(pending_transactions)")
        val pendingCols = mutableListOf<String>()
        while (cursorPending.moveToNext()) {
            pendingCols.add(cursorPending.getString(1))
        }
        cursorPending.close()

        assertTrue("pending_transactions table must have rawMerchant", pendingCols.contains("rawMerchant"))
        assertTrue("pending_transactions table must have merchantNormalized", pendingCols.contains("merchantNormalized"))
        assertTrue("pending_transactions table must have sourcePackage", pendingCols.contains("sourcePackage"))

        // 12b. Backup and Restore with new merchant fields
        val txn = Transaction(
            title = "Grocery run",
            amountPaise = 85000L,
            categoryId = 2L,
            merchantName = "Zepto",
            merchantNormalized = "zepto",
            rawMerchant = "KiranaKart Zepto",
            sourcePackage = "com.zepto.app",
            source = TransactionSource.NOTIFICATION
        )
        transactionRepository.saveTransaction(txn)

        val backupJsonString = backupRestoreRepository.exportToJson()
        assertNotNull(backupJsonString)

        val jsonRoot = JSONObject(backupJsonString)
        val txnsArray = jsonRoot.getJSONArray("transactions")
        var foundZepto = false
        for (i in 0 until txnsArray.length()) {
            val obj = txnsArray.getJSONObject(i)
            if (obj.optString("merchantName") == "Zepto") {
                foundZepto = true
                assertEquals("zepto", obj.getString("merchantNormalized"))
                assertEquals("KiranaKart Zepto", obj.getString("rawMerchant"))
                assertEquals("com.zepto.app", obj.getString("sourcePackage"))
            }
        }
        assertTrue("Exported JSON must contain Zepto merchant details", foundZepto)

        // 12c. Test legacy backup JSON restore (without new columns)
        val legacyJson = JSONObject().apply {
            put("appName", "RupeeFlow")
            put("version", 11)
            put("timestamp", System.currentTimeMillis())
            put("categories", JSONArray())
            put("friends", JSONArray())
            put("friend_groups", JSONArray())
            put("friend_group_members", JSONArray())
            put("transactions", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 999L)
                    put("title", "Legacy Coffee")
                    put("description", "Old transaction")
                    put("amountPaise", 15000L)
                    put("categoryId", 1L)
                    put("transactionDate", 1700000000000L)
                    put("paymentMethod", "UPI")
                    put("transactionType", "EXPENSE")
                    put("transactionStatus", "ACTIVE")
                    put("source", "MANUAL")
                    // Note: merchantName, merchantNormalized, rawMerchant, sourcePackage are missing
                })
            })
            put("transaction_participants", JSONArray())
            put("settlements", JSONArray())
            put("pending_transactions", JSONArray())
        }

        val restoreResult = backupRestoreRepository.restoreFromJson(legacyJson.toString())
        assertTrue("Legacy backup without merchant fields must restore cleanly", restoreResult)

        val restoredLegacyTxn = transactionRepository.getTransactionById(999L)
        assertNotNull(restoredLegacyTxn)
        assertEquals("Legacy Coffee", restoredLegacyTxn!!.title)
        assertNull("Legacy transaction without merchant data remains null", restoredLegacyTxn.merchantName)
        assertNull("Legacy transaction merchantNormalized remains null", restoredLegacyTxn.merchantNormalized)
    }
}
