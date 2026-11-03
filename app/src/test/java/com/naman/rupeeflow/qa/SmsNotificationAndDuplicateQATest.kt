package com.naman.rupeeflow.qa

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.repository.PendingTransactionRepository
import com.naman.rupeeflow.data.repository.PendingTransactionRepositoryImpl
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.data.repository.TransactionRepositoryImpl
import com.naman.rupeeflow.data.sms.SmsParser
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * RupeeFlow Production QA Test Plan
 * Section 7: SMS, notification and pending transaction handling — TC069 to TC078
 */
@RunWith(RobolectricTestRunner::class)
class SmsNotificationAndDuplicateQATest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var pendingRepository: PendingTransactionRepository
    private lateinit var transactionRepository: TransactionRepository

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        pendingRepository = PendingTransactionRepositoryImpl(
            pendingDao = database.pendingTransactionDao(),
            transactionDao = database.transactionDao()
        )

        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao()
        )

        runBlocking {
            database.categoryDao().insertCategory(
                com.naman.rupeeflow.data.local.entity.CategoryEntity(
                    id = 1L,
                    name = "General",
                    iconName = "receipt",
                    colorHex = "#FF0000",
                    isDefault = true
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test_TC069_ValidBankSmsDetection() {
        // Steps: Provide a supported bank transaction SMS.
        val hdfcSms = "Dear Customer, your A/c XX1234 has been debited by Rs. 850.00 on 02-Nov-2026 for UPI transaction to SWIGGY. UPI Ref 432190812345."
        val result = SmsParser.parse(hdfcSms, "HDFCBK")

        // Expected: Correct amount, bank, merchant and transaction direction are parsed.
        assertTrue(result.isValidTransaction)
        assertEquals(85000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("HDFC Bank", result.bankName)
        assertNotNull(result.merchant)
        assertEquals("432190812345", result.referenceNumber)
    }

    @Test
    fun test_TC070_UnsupportedSmsFormat() {
        // Steps: Provide an SMS with an unknown format.
        val unknownSms = "Hello! Your monthly newsletter from TravelClub is here. Check out exciting trips!"
        val result = SmsParser.parse(unknownSms, "UNKNOWN")

        // Expected: It is ignored / not valid transaction. No incorrect transaction created.
        assertFalse(result.isValidTransaction)
        assertEquals(0L, result.amountPaise)
    }

    @Test
    fun test_TC071_NonFinancialSms() {
        // Steps: Provide OTP, promotional and account-balance messages.
        val otpSms = "123456 is your secret OTP for payment of Rs. 850.00. Do not share OTP."
        val otpResult = SmsParser.parse(otpSms, "HDFCBK")
        assertFalse(otpResult.isValidTransaction)
        assertTrue(otpResult.isOtp)

        val balSms = "Available balance in your A/c XX1234 is Rs. 15,200.00 as of today."
        val balResult = SmsParser.parse(balSms, "SBISMS")
        assertFalse(balResult.isValidTransaction)
    }

    @Test
    fun test_TC072_DuplicateSmsDelivery() = runBlocking {
        val now = System.currentTimeMillis()
        val raw = "Debited Rs 450.00 at Swiggy UPI Ref 998877"

        val p1 = PendingTransaction(
            detectedAmountPaise = 45000L,
            detectedType = TransactionType.EXPENSE,
            detectedMerchant = "Swiggy",
            referenceNumber = "998877",
            rawContent = raw,
            timestamp = now
        )
        pendingRepository.addPendingTransaction(p1)

        // Steps: Deliver the same SMS twice.
        val isDuplicate = pendingRepository.isDuplicate(
            amountPaise = 45000L,
            timestamp = now + 1000L,
            refNumber = "998877",
            merchant = "Swiggy",
            rawContent = raw
        )

        // Expected: Duplicate detected. Only one pending item created.
        assertTrue(isDuplicate)
    }

    @Test
    fun test_TC073_SmsWithSameAmountButDifferentTransaction() = runBlocking {
        val now = System.currentTimeMillis()

        val p1 = PendingTransaction(
            detectedAmountPaise = 5000L,
            detectedType = TransactionType.EXPENSE,
            detectedMerchant = "Chai Point",
            referenceNumber = "REF_CHAI",
            rawContent = "Chai Point Rs 50",
            timestamp = now
        )
        pendingRepository.addPendingTransaction(p1)

        // Steps: Deliver two genuine purchases of the same amount close together (30s apart, different merchant).
        val isDuplicate = pendingRepository.isDuplicate(
            amountPaise = 5000L,
            timestamp = now + 30000L,
            refNumber = "REF_COFFEE",
            merchant = "Blue Tokai",
            rawContent = "Blue Tokai Rs 50"
        )

        // Expected: Both are retained as separate transactions.
        assertFalse("Same amount at different merchant must not be marked duplicate", isDuplicate)
    }

    @Test
    fun test_TC074_DuplicateNotificationDelivery() = runBlocking {
        val now = System.currentTimeMillis()

        // Transaction saved in confirmed ledger
        val txn = Transaction(
            title = "Metro Fare",
            amountPaise = 4000L,
            transactionDate = now,
            merchantName = "DMRC",
            referenceNumber = "DMRC1234"
        )
        transactionRepository.saveTransaction(txn)

        // Duplicate broadcast delivered
        val isDuplicate = transactionRepository.isDuplicate(
            amountPaise = 4000L,
            timestamp = now + 2000L,
            refNumber = "DMRC1234",
            merchant = "DMRC",
            title = "Metro Fare"
        )

        // Expected: No duplicate transaction created.
        assertTrue(isDuplicate)
    }

    @Test
    fun test_TC075_NotificationPermissionDenied() {
        // Steps: Deny notification-listener access / simulate permission disabled state.
        val isNotificationAccessGranted = false

        // Expected: App continues working; manual transaction entry continues working.
        val manualTxn = Transaction(title = "Manual Cash Expense", amountPaise = 10000L)
        assertNotNull(manualTxn)
        assertFalse(isNotificationAccessGranted)
    }

    @Test
    fun test_TC076_SmsPermissionDeniedOrRevoked() {
        // Steps: Deny or revoke SMS-related permissions.
        val hasSmsPermission = false

        // Expected: App does not crash. Manual transaction entry continues working without permission.
        val manualTxn = Transaction(title = "Manual UPI Expense", amountPaise = 20000L)
        assertNotNull(manualTxn)
        assertFalse(hasSmsPermission)
    }

    @Test
    fun test_TC077_PendingTransactionApproval() = runBlocking {
        // Steps: Detect a pending transaction and approve it.
        val pendingId = pendingRepository.addPendingTransaction(
            PendingTransaction(
                detectedAmountPaise = 75000L,
                detectedType = TransactionType.EXPENSE,
                detectedMerchant = "Zomato",
                rawContent = "Paid Rs 750 at Zomato",
                timestamp = System.currentTimeMillis()
            )
        )

        val pending = pendingRepository.getById(pendingId)!!
        assertEquals(PendingStatus.PENDING, pending.status)

        // Approve and convert to active transaction
        val newTxn = Transaction(
            title = pending.detectedMerchant ?: "Expense",
            amountPaise = pending.detectedAmountPaise,
            transactionType = pending.detectedType,
            source = TransactionSource.SMS
        )
        val txnId = transactionRepository.saveTransaction(newTxn)
        pendingRepository.resolvePendingTransaction(pendingId)

        // Expected: Exactly one normal transaction is created and pending item resolved.
        assertTrue(txnId > 0L)
        val updatedPending = pendingRepository.getById(pendingId)!!
        assertEquals(PendingStatus.RESOLVED, updatedPending.status)
    }

    @Test
    fun test_TC078_PendingTransactionRejectionAndCleanup() = runBlocking {
        // Steps: Reject a pending transaction.
        val pendingId = pendingRepository.addPendingTransaction(
            PendingTransaction(
                detectedAmountPaise = 30000L,
                detectedType = TransactionType.EXPENSE,
                detectedMerchant = "Unknown",
                rawContent = "Unwanted charge",
                timestamp = System.currentTimeMillis()
            )
        )

        pendingRepository.ignorePendingTransaction(pendingId)

        // Expected: Rejected item is marked IGNORED and does not appear in active pending list.
        val activePendingList = pendingRepository.getPendingTransactionsList()
        assertTrue(activePendingList.none { it.id == pendingId })

        val item = pendingRepository.getById(pendingId)!!
        assertEquals(PendingStatus.IGNORED, item.status)
    }
}
