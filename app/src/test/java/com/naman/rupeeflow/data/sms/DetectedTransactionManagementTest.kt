package com.naman.rupeeflow.data.sms

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
class DetectedTransactionManagementTest {

    private lateinit var app: RupeeFlowApplication

    @Before
    fun setup() {
        app = RuntimeEnvironment.getApplication() as RupeeFlowApplication
        runBlocking {
            app.appContainer.categoryRepository.ensureDefaultCategories()
        }
    }

    @Test
    fun testDeleteDetectionDoesNotAlterConfirmedFinancialLedger() = runBlocking {
        val catId = app.appContainer.categoryRepository.getAllCategoriesList().first().id

        // 1. Create a confirmed transaction
        val txn = Transaction(
            title = "Office Lunch",
            amountPaise = 35000L,
            categoryId = catId,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )
        val txnId = app.appContainer.transactionRepository.saveTransaction(txn)

        // 2. Create a pending detection
        val pending = PendingTransaction(
            detectedAmountPaise = 35000L,
            detectedType = TransactionType.EXPENSE,
            detectedMerchant = "Office Lunch",
            rawContent = "Paid ₹350",
            status = PendingStatus.PENDING
        )
        val pendingId = app.appContainer.pendingTransactionRepository.addPendingTransaction(pending)

        // 3. Delete the pending detection
        app.appContainer.pendingTransactionRepository.deletePendingTransaction(pendingId)

        // 4. Verify detection is deleted but confirmed transaction is intact
        assertNull(app.appContainer.pendingTransactionRepository.getById(pendingId))

        val retrievedTxn = app.appContainer.transactionRepository.getTransactionById(txnId)
        assertNotNull("Confirmed financial transaction must remain untouched", retrievedTxn)
        assertEquals(35000L, retrievedTxn?.amountPaise)
    }

    @Test
    fun testDeleteDetectionDoesNotAlterFriendBalancesOrSettlements() = runBlocking {
        // 1. Create friend
        val friendId = app.appContainer.friendRepository.addFriend(
            Friend(name = "Kunal", phoneNumber = "9876500000")
        )

        // 2. Create settlement
        val settlement = Settlement(
            payerId = friendId,
            receiverId = null,
            amountPaise = 50000L,
            notes = "Settled movie"
        )
        val settlementId = app.appContainer.settlementRepository.recordSettlement(settlement)

        // 3. Create and delete pending detection
        val pendingId = app.appContainer.pendingTransactionRepository.addPendingTransaction(
            PendingTransaction(
                detectedAmountPaise = 50000L,
                detectedMerchant = "Kunal",
                rawContent = "Received 500",
                status = PendingStatus.PENDING
            )
        )
        app.appContainer.pendingTransactionRepository.deletePendingTransaction(pendingId)

        // 4. Verify settlement and friend are intact
        val settlements = app.appContainer.settlementRepository.getAllSettlementsList()
        assertTrue(settlements.any { it.id == settlementId })
        val friend = app.appContainer.friendRepository.getFriendById(friendId)
        assertNotNull(friend)
    }

    @Test
    fun testDuplicatePreventionBetweenSmsAndNotification() = runBlocking {
        val amount = 42000L
        val timestamp = System.currentTimeMillis()
        val ref = "UPI_DUP_CHECK_123"
        val raw = "Paid ₹420 to Cafe Coffee Day Ref UPI_DUP_CHECK_123"

        // 1. SMS arrives first and creates pending transaction
        app.appContainer.pendingTransactionRepository.addPendingTransaction(
            PendingTransaction(
                detectedAmountPaise = amount,
                referenceNumber = ref,
                rawContent = raw,
                timestamp = timestamp,
                status = PendingStatus.PENDING
            )
        )

        // 2. Notification arrives for the same transaction
        val isDuplicate = app.appContainer.pendingTransactionRepository.isDuplicate(
            amountPaise = amount,
            timestamp = timestamp,
            refNumber = ref,
            rawContent = raw
        )

        assertTrue("Duplicate detection between SMS and notification must return true", isDuplicate)
    }
}
