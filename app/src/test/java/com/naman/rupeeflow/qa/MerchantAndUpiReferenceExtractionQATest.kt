package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.data.local.entity.TransactionEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.data.sms.SmsParser
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
 * QA Test Suite for Merchant Name and UPI Reference Number Auto-Extraction.
 * Validates all 17 requirements:
 * 1. UPI SMS containing UTR.
 * 2. SMS containing UPI Ref No.
 * 3. SMS containing RRN.
 * 4. Notification containing bank reference.
 * 5. Merchant and reference extracted together.
 * 6. Merchant present but reference missing.
 * 7. Reference present but merchant missing.
 * 8. Unrelated 12-digit account number (must NOT be extracted as reference).
 * 9. Google Pay app transaction ID (must NOT be extracted as UPI RRN).
 * 10. PhonePe app transaction ID (must NOT be extracted as UPI RRN).
 * 11. Different bank SMS formats (HDFC, SBI, ICICI, Axis, Kotak, Bank of Baroda).
 * 12. Pending transaction approval (preserves merchant & UPI ref).
 * 13. Manual reference correction (editing persists).
 * 14. Editing existing transaction (preserves & updates).
 * 15. Backup and restore (handles old and new backups).
 * 16. Duplicate transaction detection (uses UPI ref as signal, no false positives).
 * 17. Merchant category learning regression.
 */
@RunWith(RobolectricTestRunner::class)
class MerchantAndUpiReferenceExtractionQATest {

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

        merchantPreferenceRepository = MerchantPreferenceRepositoryImpl(
            inputUsageDao = database.inputUsageDao()
        )

        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao(),
            context = context,
            merchantPreferenceRepository = merchantPreferenceRepository
        )

        pendingRepository = PendingTransactionRepositoryImpl(
            pendingDao = database.pendingTransactionDao(),
            transactionDao = database.transactionDao()
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

    // 1. UPI SMS containing UTR
    @Test
    fun test1_upiSmsContainingUtr() {
        val sms = "Rs. 500.00 debited towards Swiggy on 19-Nov-2024. UTR: 123456789012. Bal Rs 4500."
        val result = SmsParser.parse(sms)
        assertTrue("Should be valid transaction", result.isValidTransaction)
        assertEquals(50000L, result.amountPaise)
        assertEquals("Swiggy", result.merchant)
        assertEquals("123456789012", result.upiReferenceNumber)
    }

    // 2. SMS containing UPI Ref No
    @Test
    fun test2_smsContainingUpiRefNo() {
        val sms = "Rs.450.00 debited to VPA merchant@upi. UPI Ref No 634512345678."
        val result = SmsParser.parse(sms)
        assertTrue("Should be valid transaction", result.isValidTransaction)
        assertEquals(45000L, result.amountPaise)
        assertEquals("634512345678", result.upiReferenceNumber)
    }

    // 3. SMS containing RRN
    @Test
    fun test3_smsContainingRrn() {
        val sms = "Debited Rs.300.00 to Chai Point. RRN 987654321012. Avl Bal Rs 2000."
        val result = SmsParser.parse(sms)
        assertTrue("Should be valid transaction", result.isValidTransaction)
        assertEquals(30000L, result.amountPaise)
        assertEquals("Chai Point", result.merchant)
        assertEquals("987654321012", result.upiReferenceNumber)
    }

    // 4. Notification containing bank reference
    @Test
    fun test4_notificationContainingBankReference() {
        val title = "Payment Successful"
        val body = "Paid Rs 250 to Domino's. Bank Reference Number: 432412345678."
        val pkg = "com.google.android.apps.nbu.paisa.user"
        val result = SmsParser.parseNotification(title = title, body = body, packageName = pkg)
        assertTrue("Should be valid transaction", result.isValidTransaction)
        assertEquals(25000L, result.amountPaise)
        assertEquals("Domino's", result.merchant)
        assertEquals("432412345678", result.upiReferenceNumber)
    }

    // 5. Merchant and reference extracted together
    @Test
    fun test5_merchantAndReferenceExtractedTogether() {
        val sms = "Dear SBI User, your A/c ending 1234 debited by Rs.250.00 on 19Nov24 transfer to Swiggy Ref No 432190812345. Bal Rs.10,500.00"
        val result = SmsParser.parse(sms, "SBIN")
        assertTrue("Should be valid transaction", result.isValidTransaction)
        assertEquals(25000L, result.amountPaise)
        assertEquals("Swiggy", result.merchant)
        assertEquals("432190812345", result.upiReferenceNumber)
    }

    // 6. Merchant present but reference missing
    @Test
    fun test6_merchantPresentButReferenceMissing() {
        val sms = "Paid Rs 300.00 to Starbucks from A/c XX1234. Avl Bal Rs 5000."
        val result = SmsParser.parse(sms)
        assertTrue("Should be valid transaction", result.isValidTransaction)
        assertEquals("Starbucks", result.merchant)
        assertNull("UPI ref should be null when not present", result.upiReferenceNumber)
    }

    // 7. Reference present but merchant missing
    @Test
    fun test7_referencePresentButMerchantMissing() {
        val sms = "Rs 500.00 debited from A/c XX1234. UTR 123456789012. Avl Bal Rs 2000."
        val result = SmsParser.parse(sms)
        assertTrue("Should be valid transaction", result.isValidTransaction)
        assertEquals("123456789012", result.upiReferenceNumber)
        assertNull("Merchant should be null when not present", result.merchant)
    }

    // 8. Unrelated 12-digit account number (must NOT be extracted as reference)
    @Test
    fun test8_unrelated12DigitAccountNumberNotExtractedAsReference() {
        val sms = "Dear customer, Rs 600.00 debited from A/c 543212345678. Bal Rs 10000. Not you? Call 180012345678."
        val result = SmsParser.parse(sms)
        assertTrue("Should be valid transaction", result.isValidTransaction)
        assertEquals(60000L, result.amountPaise)
        assertNull("12-digit account number or helpline must NOT be extracted as UPI ref", result.upiReferenceNumber)
    }

    // 9. Google Pay app transaction ID (must NOT be extracted as UPI RRN)
    @Test
    fun test9_googlePayAppTransactionIdNotExtractedAsUpiRrn() {
        // Solely Google Pay ID
        val sms1 = "Paid Rs.500.00 on Google Pay. Google Pay Transaction ID: CICAgICAgICAgA."
        val result1 = SmsParser.parse(sms1, "com.google.android.apps.nbu.paisa.user")
        assertNull("Google Pay internal ID must NOT be extracted as UPI ref", result1.upiReferenceNumber)

        // Google Pay ID alongside actual bank UPI Ref No
        val sms2 = "Paid Rs.500.00 on Google Pay. Google Pay Transaction ID: CICAgICAgICAgA. UPI Ref No 402312345678."
        val result2 = SmsParser.parse(sms2, "com.google.android.apps.nbu.paisa.user")
        assertEquals("402312345678", result2.upiReferenceNumber)
    }

    // 10. PhonePe app transaction ID (must NOT be extracted as UPI RRN)
    @Test
    fun test10_phonePeAppTransactionIdNotExtractedAsUpiRrn() {
        // Solely PhonePe ID
        val sms1 = "Paid Rs.200.00 to Zomato. PhonePe Transaction ID: T2411191430000123456789."
        val result1 = SmsParser.parse(sms1, "com.phonepe.app")
        assertEquals("Zomato", result1.merchant)
        assertNull("PhonePe internal ID must NOT be extracted as UPI ref", result1.upiReferenceNumber)

        // PhonePe ID alongside actual Bank Reference Number
        val sms2 = "Paid Rs.200.00 to Zomato. PhonePe Txn ID: T2411191430000123456789. Bank Reference Number: 987612345678."
        val result2 = SmsParser.parse(sms2, "com.phonepe.app")
        assertEquals("Zomato", result2.merchant)
        assertEquals("987612345678", result2.upiReferenceNumber)
    }

    // 11. Different bank SMS formats
    @Test
    fun test11_differentBankSmsFormats() {
        // HDFC Bank
        val hdfc = "HDFC Bank: Rs 850.00 debited from a/c **1234 to Blinkit on 19-11-24. UPI Ref 321456987123."
        val hdfcRes = SmsParser.parse(hdfc, "HDFCBK")
        assertEquals("Blinkit", hdfcRes.merchant)
        assertEquals("321456987123", hdfcRes.upiReferenceNumber)

        // SBI
        val sbi = "Dear SBI User, your A/c ending 5678 debited by Rs.1200.00 on 19Nov24 transfer to Amazon Ref No 654321987123."
        val sbiRes = SmsParser.parse(sbi, "SBIN")
        assertEquals("Amazon", sbiRes.merchant)
        assertEquals("654321987123", sbiRes.upiReferenceNumber)

        // ICICI Bank
        val icici = "ICICI Bank: Acct XX4321 debited for Rs 340.00 on 19-Nov-24. Info: UPI/Zomato. UPI Ref no 789456123012."
        val iciciRes = SmsParser.parse(icici, "ICICIB")
        assertEquals("Zomato", iciciRes.merchant)
        assertEquals("789456123012", iciciRes.upiReferenceNumber)

        // Axis Bank
        val axis = "Axis Bank: Rs. 499.00 debited from A/c XX7890 to Netflix. UTR 456789123456."
        val axisRes = SmsParser.parse(axis, "AXISBK")
        assertEquals("Netflix", axisRes.merchant)
        assertEquals("456789123456", axisRes.upiReferenceNumber)

        // Kotak Mahindra Bank
        val kotak = "Sent Rs. 650.00 from Kotak Bank AC *3456 to Swiggy on 19-11-2024. UPI Ref No 987123456012."
        val kotakRes = SmsParser.parse(kotak, "KOTAKB")
        assertEquals("Swiggy", kotakRes.merchant)
        assertEquals("987123456012", kotakRes.upiReferenceNumber)

        // Bank of Baroda
        val bob = "Bank of Baroda: Rs 300.00 debited from A/c *8901 to Chai Point. RRN: 654987321012."
        val bobRes = SmsParser.parse(bob, "BOBTXN")
        assertEquals("Chai Point", bobRes.merchant)
        assertEquals("654987321012", bobRes.upiReferenceNumber)
    }

    // 12. Pending transaction approval preserves merchant & UPI ref
    @Test
    fun test12_pendingTransactionApprovalPreservesMerchantAndUpiRef() = runBlocking {
        val pendingEntity = PendingTransactionEntity(
            detectedAmountPaise = 75000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC",
            detectedMerchant = "Domino's",
            rawMerchant = "Domino's",
            referenceNumber = "634512345678",
            upiReferenceNumber = "634512345678",
            rawContent = "Paid Rs 750 to Domino's. UPI Ref 634512345678",
            timestamp = System.currentTimeMillis(),
            status = PendingStatus.PENDING
        )
        val pendingId = database.pendingTransactionDao().insert(pendingEntity)
        val domainPending = pendingRepository.getById(pendingId)
        assertNotNull(domainPending)
        assertEquals("Domino's", domainPending?.detectedMerchant)
        assertEquals("634512345678", domainPending?.upiReferenceNumber)

        // Approve and convert into confirmed Transaction
        val confirmedTxn = Transaction(
            title = "Dinner",
            amountPaise = domainPending!!.detectedAmountPaise,
            categoryId = 1L,
            merchantName = domainPending.detectedMerchant,
            rawMerchant = domainPending.rawMerchant,
            referenceNumber = domainPending.referenceNumber,
            upiReferenceNumber = domainPending.upiReferenceNumber
        )
        val txnId = transactionRepository.saveTransaction(confirmedTxn)
        pendingRepository.resolvePendingTransaction(pendingId)

        val retrievedTxn = transactionRepository.getTransactionById(txnId)
        assertNotNull(retrievedTxn)
        assertEquals("Dinner", retrievedTxn?.title)
        assertEquals("Domino's", retrievedTxn?.merchantName)
        assertEquals("634512345678", retrievedTxn?.upiReferenceNumber)
        assertEquals("634512345678", retrievedTxn?.referenceNumber)
    }

    // 13. Manual reference correction (editing persists)
    @Test
    fun test13_manualReferenceCorrection() = runBlocking {
        val initialTxn = Transaction(
            title = "Coffee",
            amountPaise = 20000L,
            categoryId = 1L,
            merchantName = "Blue Tokai",
            referenceNumber = null,
            upiReferenceNumber = null
        )
        val txnId = transactionRepository.saveTransaction(initialTxn)
        val saved = transactionRepository.getTransactionById(txnId)!!
        assertNull(saved.upiReferenceNumber)

        // Correct manually
        val updated = saved.copy(
            upiReferenceNumber = "999888777666",
            referenceNumber = "999888777666"
        )
        transactionRepository.saveTransaction(updated)

        val corrected = transactionRepository.getTransactionById(txnId)!!
        assertEquals("999888777666", corrected.upiReferenceNumber)
        assertEquals("Blue Tokai", corrected.merchantName)
    }

    // 14. Editing existing transaction (preserves & updates)
    @Test
    fun test14_editingExistingTransactionPreservesAndUpdates() = runBlocking {
        val txn = Transaction(
            title = "Original Title",
            description = "Some description",
            amountPaise = 15000L,
            categoryId = 1L,
            merchantName = "Old Merchant",
            referenceNumber = "111222333444",
            upiReferenceNumber = "111222333444"
        )
        val id = transactionRepository.saveTransaction(txn)
        val existing = transactionRepository.getTransactionById(id)!!

        // Update merchant & reference while preserving title and description
        val modified = existing.copy(
            merchantName = "Updated Merchant",
            merchantNormalized = MerchantNormalizer.normalize("Updated Merchant"),
            upiReferenceNumber = "555666777888",
            referenceNumber = "555666777888"
        )
        transactionRepository.saveTransaction(modified)

        val retrieved = transactionRepository.getTransactionById(id)!!
        assertEquals("Original Title", retrieved.title)
        assertEquals("Some description", retrieved.description)
        assertEquals("Updated Merchant", retrieved.merchantName)
        assertEquals("555666777888", retrieved.upiReferenceNumber)
    }

    // 15. Backup and restore (handles old and new backups)
    @Test
    fun test15_backupAndRestoreHandlesOldAndNewBackups() = runBlocking {
        // A. Export and restore new backup
        val txn = Transaction(
            title = "Weekly Grocery",
            amountPaise = 125000L,
            categoryId = 2L,
            merchantName = "Blinkit",
            referenceNumber = "888777666555",
            upiReferenceNumber = "888777666555"
        )
        transactionRepository.saveTransaction(txn)

        val backupJson = backupRestoreRepository.exportToJson()
        assertTrue(backupJson.contains("upiReferenceNumber"))
        assertTrue(backupJson.contains("888777666555"))
        assertTrue(backupJson.contains("Blinkit"))

        // Delete from DB and restore
        database.clearAllTables()
        val restoredSuccess = backupRestoreRepository.restoreFromJson(backupJson)
        assertTrue("Restore should succeed", restoredSuccess)

        val allTxns = transactionRepository.getAllTransactionsList()
        val restoredTxn = allTxns.firstOrNull { it.merchantName == "Blinkit" }
        assertNotNull(restoredTxn)
        assertEquals("888777666555", restoredTxn?.upiReferenceNumber)

        // B. Test legacy backup without upiReferenceNumber field
        database.clearAllTables()
        val legacyJson = JSONObject().apply {
            put("appName", "RupeeFlow")
            put("version", 4)
            put("exportTimestamp", System.currentTimeMillis())
            put("categories", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("name", "Food")
                    put("iconName", "restaurant")
                    put("colorHex", "#F97316")
                    put("isDefault", true)
                })
            })
            put("transactions", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 999L)
                    put("title", "Legacy Dinner")
                    put("amountPaise", 45000L)
                    put("categoryId", 1L)
                    put("transactionDate", System.currentTimeMillis())
                    put("paymentMethod", "UPI")
                    put("splitMethod", "EQUAL")
                    put("transactionType", "EXPENSE")
                    put("transactionStatus", "ACTIVE")
                    put("source", "SMS")
                    put("merchantName", "McDonald's")
                    put("referenceNumber", "777666555444")
                    // No upiReferenceNumber field!
                })
            })
        }.toString()

        val legacyRestoreSuccess = backupRestoreRepository.restoreFromJson(legacyJson)
        assertTrue("Legacy restore should succeed cleanly", legacyRestoreSuccess)
        val legacyTxn = transactionRepository.getAllTransactionsList().firstOrNull { it.merchantName == "McDonald's" }
        assertNotNull(legacyTxn)
        assertEquals("777666555444", legacyTxn?.referenceNumber)
        assertEquals("777666555444", legacyTxn?.upiReferenceNumber)
    }

    // 16. Duplicate transaction detection uses UPI ref as signal
    @Test
    fun test16_duplicateTransactionDetectionUsesUpiRefAsSignal() = runBlocking {
        val now = System.currentTimeMillis()
        val txn = Transaction(
            title = "Lunch",
            amountPaise = 35000L,
            categoryId = 1L,
            transactionDate = now,
            merchantName = "Haldiram's",
            referenceNumber = "634512345678",
            upiReferenceNumber = "634512345678"
        )
        transactionRepository.saveTransaction(txn)

        // A. Same UPI reference -> Duplicate!
        val isDupSameRef = transactionRepository.isDuplicate(
            amountPaise = 35000L,
            timestamp = now + 1000L,
            refNumber = "634512345678",
            merchant = "Haldiram's"
        )
        assertTrue("Matching UPI reference must be recognized as duplicate", isDupSameRef)

        // B. Different UPI reference -> Legitimate separate purchase!
        val isDupDiffRef = transactionRepository.isDuplicate(
            amountPaise = 35000L,
            timestamp = now + 30_000L,
            refNumber = "999888111222",
            merchant = "Haldiram's"
        )
        assertFalse("Different UPI reference must NOT be rejected as duplicate", isDupDiffRef)

        // C. Different merchant -> Legitimate separate purchase!
        val isDupDiffMerchant = transactionRepository.isDuplicate(
            amountPaise = 35000L,
            timestamp = now + 10_000L,
            refNumber = null,
            merchant = "Subway"
        )
        assertFalse("Different merchant must NOT be rejected as duplicate", isDupDiffMerchant)
    }

    // 17. Merchant category learning regression
    @Test
    fun test17_merchantCategoryLearningRegression() = runBlocking {
        val txn = Transaction(
            title = "Dinner at Domino's",
            amountPaise = 55000L,
            categoryId = 1L, // Food
            merchantName = "Domino's",
            referenceNumber = "123456789012",
            upiReferenceNumber = "123456789012"
        )
        transactionRepository.saveTransaction(txn)

        // Check auto-learned merchant category
        val learnedCatId = transactionRepository.getLatestCategoryForMerchant("Domino's")
        assertEquals(1L, learnedCatId)
    }
}

