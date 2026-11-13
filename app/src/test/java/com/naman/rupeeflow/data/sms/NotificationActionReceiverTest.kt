package com.naman.rupeeflow.data.sms

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.naman.rupeeflow.RupeeFlowApplication
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NotificationActionReceiverTest {

    private lateinit var app: RupeeFlowApplication
    private lateinit var receiver: TransactionNotificationActionReceiver

    @Before
    fun setup() {
        app = RuntimeEnvironment.getApplication() as RupeeFlowApplication
        receiver = TransactionNotificationActionReceiver()
        runBlocking {
            app.appContainer.categoryRepository.ensureDefaultCategories()
        }
    }

    private fun createPendingDetection(
        id: Long = 0L,
        amountPaise: Long = 50000L,
        merchant: String? = "Swiggy",
        status: PendingStatus = PendingStatus.PENDING,
        refNumber: String? = "REF123456"
    ): Long = runBlocking {
        val pending = PendingTransaction(
            id = id,
            detectedAmountPaise = amountPaise,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC Bank",
            detectedMerchant = merchant,
            referenceNumber = refNumber,
            rawContent = "Paid ₹500 to Swiggy",
            status = status,
            source = TransactionSource.NOTIFICATION
        )
        app.appContainer.pendingTransactionRepository.addPendingTransaction(pending)
    }

    @Test
    fun test1_AddExpenseFromNotificationCreatesConfirmedTransactionAndResolvesPending() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 75000L, merchant = "Chai Point")

        val intent = Intent(TransactionNotificationActionReceiver.ACTION_ADD_EXPENSE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
            putExtra(TransactionNotificationActionReceiver.EXTRA_NOTIFICATION_ID, pendingId.toInt())
        }

        receiver.onReceive(app, intent)

        // Wait a small amount for coroutine
        kotlinx.coroutines.delay(200)

        val updatedPending = app.appContainer.pendingTransactionRepository.getById(pendingId)
        assertNotNull(updatedPending)
        assertEquals(PendingStatus.RESOLVED, updatedPending?.status)

        val txns = app.appContainer.transactionRepository.getAllTransactionsList()
        val createdTxn = txns.find { it.amountPaise == 75000L && it.title == "Chai Point" }
        assertNotNull("Confirmed transaction must be created in ledger", createdTxn)
        assertEquals(TransactionType.EXPENSE, createdTxn?.transactionType)
    }

    @Test
    fun test2_IgnoreFromNotificationSetsStatusToIgnoredWithoutCreatingTransaction() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 30000L, merchant = "Spam Pay")

        val intent = Intent(TransactionNotificationActionReceiver.ACTION_IGNORE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }

        receiver.onReceive(app, intent)
        kotlinx.coroutines.delay(200)

        val updated = app.appContainer.pendingTransactionRepository.getById(pendingId)
        assertNotNull(updated)
        assertEquals(PendingStatus.IGNORED, updated?.status)

        val txns = app.appContainer.transactionRepository.getAllTransactionsList()
        assertNull(txns.find { it.title == "Spam Pay" })
    }

    @Test
    fun test3_DeleteFromNotificationRemovesPendingDetection() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 20000L, merchant = "Temp Pay")

        val intent = Intent(TransactionNotificationActionReceiver.ACTION_DELETE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }

        receiver.onReceive(app, intent)
        kotlinx.coroutines.delay(200)

        val item = app.appContainer.pendingTransactionRepository.getById(pendingId)
        assertNull("Pending detection must be deleted", item)
    }

    @Test
    fun test4_AddExpenseTappedTwiceIsIdempotentAndDoesNotCreateDuplicates() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 45000L, merchant = "Zomato", refNumber = "REF_ZOMATO_DUP")

        val intent = Intent(TransactionNotificationActionReceiver.ACTION_ADD_EXPENSE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }

        // Tap once
        receiver.onReceive(app, intent)
        kotlinx.coroutines.delay(200)

        // Tap twice
        receiver.onReceive(app, intent)
        kotlinx.coroutines.delay(200)

        val txns = app.appContainer.transactionRepository.getAllTransactionsList()
        val matchCount = txns.count { it.amountPaise == 45000L && it.title == "Zomato" }
        assertEquals("Duplicate taps must not create duplicate transactions", 1, matchCount)
    }

    @Test
    fun test5_IgnoreTappedTwiceIsIdempotent() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 15000L, merchant = "Tea Shop")

        val intent = Intent(TransactionNotificationActionReceiver.ACTION_IGNORE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }

        receiver.onReceive(app, intent)
        kotlinx.coroutines.delay(100)
        receiver.onReceive(app, intent)
        kotlinx.coroutines.delay(100)

        val updated = app.appContainer.pendingTransactionRepository.getById(pendingId)
        assertEquals(PendingStatus.IGNORED, updated?.status)
    }

    @Test
    fun test6_DeleteTappedTwiceIsIdempotent() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 12000L, merchant = "Bakery")

        val intent = Intent(TransactionNotificationActionReceiver.ACTION_DELETE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }

        receiver.onReceive(app, intent)
        kotlinx.coroutines.delay(100)
        receiver.onReceive(app, intent)
        kotlinx.coroutines.delay(100)

        assertNull(app.appContainer.pendingTransactionRepository.getById(pendingId))
    }

    @Test
    fun test7_AddExpenseAfterDeletionDoesNotRecreate() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 55000L, merchant = "Deleted Pay")

        // First delete
        app.appContainer.pendingTransactionRepository.deletePendingTransaction(pendingId)

        // Then attempt add
        val intent = Intent(TransactionNotificationActionReceiver.ACTION_ADD_EXPENSE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }
        receiver.onReceive(app, intent)
        kotlinx.coroutines.delay(200)

        val txns = app.appContainer.transactionRepository.getAllTransactionsList()
        assertNull(txns.find { it.title == "Deleted Pay" })
    }

    @Test
    fun test8_DeleteAfterApprovalNeverDeletesConfirmedTransaction() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 90000L, merchant = "Grocery Store")

        // Approve
        val addIntent = Intent(TransactionNotificationActionReceiver.ACTION_ADD_EXPENSE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }
        receiver.onReceive(app, addIntent)
        kotlinx.coroutines.delay(200)

        val txnsBefore = app.appContainer.transactionRepository.getAllTransactionsList()
        assertTrue(txnsBefore.any { it.title == "Grocery Store" })

        // Attempt Delete on approved detection
        val deleteIntent = Intent(TransactionNotificationActionReceiver.ACTION_DELETE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }
        receiver.onReceive(app, deleteIntent)
        kotlinx.coroutines.delay(200)

        val txnsAfter = app.appContainer.transactionRepository.getAllTransactionsList()
        assertTrue("Approved confirmed transaction MUST remain intact in ledger", txnsAfter.any { it.title == "Grocery Store" })
    }

    @Test
    fun test9_IgnoreAfterApprovalIsSafeNoOp() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 80000L, merchant = "Uber")

        val addIntent = Intent(TransactionNotificationActionReceiver.ACTION_ADD_EXPENSE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }
        receiver.onReceive(app, addIntent)
        kotlinx.coroutines.delay(200)

        val ignoreIntent = Intent(TransactionNotificationActionReceiver.ACTION_IGNORE).apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
        }
        receiver.onReceive(app, ignoreIntent)
        kotlinx.coroutines.delay(200)

        val pending = app.appContainer.pendingTransactionRepository.getById(pendingId)
        assertEquals("Already resolved detection must remain RESOLVED", PendingStatus.RESOLVED, pending?.status)
    }

    @Test
    fun test11_MissingDetectionIdHandledGracefully() {
        val intent = Intent(TransactionNotificationActionReceiver.ACTION_ADD_EXPENSE)
        // No extras
        receiver.onReceive(app, intent)
    }

    @Test
    fun test12_InvalidActionPayloadHandledGracefully() {
        val intent = Intent("UNKNOWN_ACTION").apply {
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, 99999L)
        }
        receiver.onReceive(app, intent)
    }

    @Test
    fun test16_PendingDetectionRestorationFromIgnored() = runBlocking {
        val pendingId = createPendingDetection(amountPaise = 35000L, merchant = "Restorable Merchant")
        app.appContainer.pendingTransactionRepository.ignorePendingTransaction(pendingId)

        assertEquals(PendingStatus.IGNORED, app.appContainer.pendingTransactionRepository.getById(pendingId)?.status)

        app.appContainer.pendingTransactionRepository.restorePendingTransaction(pendingId)
        assertEquals(PendingStatus.PENDING, app.appContainer.pendingTransactionRepository.getById(pendingId)?.status)
    }
}

