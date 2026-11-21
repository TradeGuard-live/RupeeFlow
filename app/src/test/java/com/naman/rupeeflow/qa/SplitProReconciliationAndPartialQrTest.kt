package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.upi.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Comprehensive Automated QA Test Suite for:
 * 1. Bug 1: Outstanding calculations & single source of truth ledger reconciliation.
 * 2. Bug 2: Automatic UPI settlement reconciliation from SMS/Notifications.
 * 3. Bug 3: Partial QR generation and amount independence.
 */
@RunWith(RobolectricTestRunner::class)
class SplitProReconciliationAndPartialQrTest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var friendRepository: FriendRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var upiSettlementManager: UpiSettlementManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val merchantRepo = MerchantPreferenceRepositoryImpl(database.inputUsageDao())
        friendRepository = FriendRepositoryImpl(database.friendDao())
        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao(),
            context = context,
            merchantPreferenceRepository = merchantRepo
        )
        settlementRepository = SettlementRepositoryImpl(
            settlementDao = database.settlementDao(),
            friendDao = database.friendDao(),
            context = context
        )
        upiSettlementManager = UpiSettlementManager(
            settlementRepository = settlementRepository,
            groupActivityRepository = null,
            friendRepository = friendRepository
        )

        runBlocking {
            database.categoryDao().insertCategory(
                com.naman.rupeeflow.data.local.entity.CategoryEntity(
                    id = 1L,
                    name = "Food",
                    colorHex = "#FF5722",
                    iconName = "restaurant"
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // =========================================================================
    // SUITE 1: OUTSTANDING CALCULATION & LEDGER CONSISTENCY (BUG 1)
    // =========================================================================

    @Test
    fun test01_unpaidExpensesAppearInOutstanding() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Manan", upiId = "manan@upi")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        transactionRepository.saveTransaction(
            Transaction(
                title = "Dinner",
                amountPaise = 50000L, // 500.00
                paidByFriendId = null, // Me paid
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 25000L))
            )
        )

        val txns = transactionRepository.getAllTransactionsList()
        val settlements = settlementRepository.getAllSettlementsList()

        val reconciliation = LedgerCalculator.reconcileFriendLedger(friend, txns, settlements)

        assertEquals("Manan owes 250", 25000L, reconciliation.friend.netBalancePaise)
        assertEquals("Outstanding count should be 1", 1, reconciliation.unsettledExpenses.size)
        assertEquals("Remaining amount matches", 25000L, reconciliation.unsettledExpenses[0].remainingOutstandingPaise)
        assertEquals("Agreement between netBalance and outstanding sum",
            reconciliation.friend.netBalancePaise,
            reconciliation.unsettledExpenses.sumOf { it.remainingOutstandingPaise }
        )
    }

    @Test
    fun test02_fullySettledExpensesDisappearFromOutstandingAndRemainInAllActivity() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Manan", upiId = "manan@upi")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        transactionRepository.saveTransaction(
            Transaction(
                title = "Snacks",
                amountPaise = 10000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 10000L))
            )
        )

        // Fully settle
        settlementRepository.recordSettlement(
            Settlement(
                payerId = friendId,
                receiverId = null,
                amountPaise = 10000L,
                paymentMethod = PaymentMethod.UPI,
                upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
                upiVerificationStatus = SettlementVerificationStatus.AUTOMATIC_VERIFIED
            )
        )

        val txns = transactionRepository.getAllTransactionsList()
        val settlements = settlementRepository.getAllSettlementsList()

        val reconciliation = LedgerCalculator.reconcileFriendLedger(friend, txns, settlements)

        assertEquals("Net balance is zero", 0L, reconciliation.friend.netBalancePaise)
        assertEquals("Friend status is SETTLED", FriendBalanceStatus.SETTLED, reconciliation.friend.status)
        assertTrue("Outstanding must be empty", reconciliation.unsettledExpenses.isEmpty())
        assertEquals("All activity contains the expense", 1, reconciliation.allExpenseHistory.size)
        assertTrue("Expense in history is marked fully settled", reconciliation.allExpenseHistory[0].isFullySettled)
    }

    @Test
    fun test03_partiallySettledExpensesShowCorrectRemaining() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Manan", upiId = "manan@upi")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        transactionRepository.saveTransaction(
            Transaction(
                title = "Dinner",
                amountPaise = 50000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 50000L))
            )
        )

        // Settle 300 out of 500
        settlementRepository.recordSettlement(
            Settlement(
                payerId = friendId,
                receiverId = null,
                amountPaise = 30000L,
                upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
                upiVerificationStatus = SettlementVerificationStatus.AUTOMATIC_VERIFIED
            )
        )

        val txns = transactionRepository.getAllTransactionsList()
        val settlements = settlementRepository.getAllSettlementsList()

        val reconciliation = LedgerCalculator.reconcileFriendLedger(friend, txns, settlements)

        assertEquals("Net balance is 200", 20000L, reconciliation.friend.netBalancePaise)
        assertEquals("Outstanding count is 1", 1, reconciliation.unsettledExpenses.size)
        val item = reconciliation.unsettledExpenses[0]
        assertEquals("Original share: 500", 50000L, item.originalShareAmountPaise)
        assertEquals("Settled amount: 300", 30000L, item.settledAmountPaise)
        assertEquals("Remaining outstanding: 200", 20000L, item.remainingOutstandingPaise)
        assertTrue("Is marked partially settled", item.isPartiallySettled)
        assertFalse("Is not fully settled", item.isFullySettled)
    }

    @Test
    fun test04_userScreenshotScenario_MananOwes350_OutstandingReconciled() = runBlocking {
        // Reproduces user screenshot bug:
        // Friend: Manan owes me ₹350.00
        // Expected: Outstanding count: 2, Outstanding total: ₹350.00
        // Dinner: Original ₹500, Settled ₹300, Remaining ₹200
        // Gas bill: Original ₹250, Settled ₹100, Remaining ₹150
        // Snacks: Original ₹100, Settled ₹100, Remaining ₹0 (in All Activity only)
        val friendId = friendRepository.addFriend(
            Friend(name = "Manan", upiId = "manan@upi")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        val now = System.currentTimeMillis()

        // 1. Dinner ₹500
        transactionRepository.saveTransaction(
            Transaction(
                title = "Dinner",
                amountPaise = 50000L,
                transactionDate = now - 300000,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 50000L))
            )
        )

        // 2. Gas bill ₹250
        transactionRepository.saveTransaction(
            Transaction(
                title = "Gas bill",
                amountPaise = 25000L,
                transactionDate = now - 200000,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 25000L))
            )
        )

        // 3. Snacks ₹100
        transactionRepository.saveTransaction(
            Transaction(
                title = "Snacks",
                amountPaise = 10000L,
                transactionDate = now - 100000,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 10000L))
            )
        )

        // Settlements totaling 500:
        // 300 on Dinner + 100 on Gas bill + 100 on Snacks = 500 settled
        // Total expenses = 500 + 250 + 100 = 850
        // Remaining = 850 - 500 = 350
        settlementRepository.recordSettlement(
            Settlement(
                payerId = friendId,
                receiverId = null,
                amountPaise = 50000L,
                settlementDate = now - 50000,
                upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
                upiVerificationStatus = SettlementVerificationStatus.AUTOMATIC_VERIFIED
            )
        )

        val txns = transactionRepository.getAllTransactionsList()
        val settlements = settlementRepository.getAllSettlementsList()

        val reconciliation = LedgerCalculator.reconcileFriendLedger(friend, txns, settlements)

        // Strict assertions matching user requirement:
        assertEquals("Friend balance card must show 350.00", 35000L, reconciliation.friend.netBalancePaise)
        assertEquals("Outstanding count must be 2 (Snacks is fully settled)", 2, reconciliation.unsettledExpenses.size)
        assertEquals("Outstanding sum must be exactly 350.00", 35000L, reconciliation.unsettledExpenses.sumOf { it.remainingOutstandingPaise })

        // Check individual items
        val dinnerItem = reconciliation.unsettledExpenses.find { it.transaction.title == "Dinner" }
        val gasItem = reconciliation.unsettledExpenses.find { it.transaction.title == "Gas bill" }
        val snacksItem = reconciliation.unsettledExpenses.find { it.transaction.title == "Snacks" }

        assertNull("Fully settled Snacks must NOT be in Outstanding", snacksItem)
        assertNotNull("Dinner must be in Outstanding", dinnerItem)
        assertNotNull("Gas bill must be in Outstanding", gasItem)

        assertEquals("Outstanding sum reconciles with balance card",
            reconciliation.friend.netBalancePaise,
            reconciliation.unsettledExpenses.sumOf { it.remainingOutstandingPaise }
        )

        // All Activity verification
        assertEquals("All Activity must contain all 3 historical expenses", 3, reconciliation.allExpenseHistory.size)
    }

    // =========================================================================
    // SUITE 2: PARTIAL QR GENERATION & INDEPENDENCE (BUG 3)
    // =========================================================================

    @Test
    fun test05_partialQrGeneration_containsExactUserEnteredAmount() {
        val totalOutstandingPaise = 100000L // ₹1,000.00
        val userEnteredPaise = 30000L // ₹300.00

        val uri = UpiQrCodeGenerator.buildSettlementQrUri(
            receiverUpiId = "user@bank",
            receiverName = "RupeeFlow User",
            amountPaise = userEnteredPaise,
            note = "RupeeFlow settlement from Manan",
            transactionRef = "RFQR123"
        )

        // Verify URI contains am=300.00 and cu=INR, NOT 1000.00
        assertTrue("URI must contain am=300.00", uri.contains("am=300.00"))
        assertTrue("URI must contain cu=INR", uri.contains("cu=INR"))
        assertFalse("URI must NOT contain full outstanding am=1000.00", uri.contains("am=1000.00"))

        // Verify remaining balance calculation
        val remainingPaise = totalOutstandingPaise - userEnteredPaise
        assertEquals("Remaining balance must be 700.00", 70000L, remainingPaise)
    }

    @Test
    fun test06_initiateQrSettlement_createsPendingSessionWithoutAffectingLedger() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Manan", upiId = "manan@upi")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        // Total outstanding is 1000
        transactionRepository.saveTransaction(
            Transaction(
                title = "Shopping",
                amountPaise = 100000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 100000L))
            )
        )

        // User generates QR for 300
        val settlementId = upiSettlementManager.initiateQrSettlement(
            friend = friend,
            amountPaise = 30000L,
            isPayingFriend = false,
            qrReference = "RFQRTEST999"
        )

        val initiated = settlementRepository.getSettlementById(settlementId)!!
        assertEquals("Settlement amount is 300", 30000L, initiated.amountPaise)
        assertEquals("Status is INITIATED", SettlementPaymentStatus.INITIATED, initiated.upiPaymentStatus)
        assertEquals("Verification status is PENDING", SettlementVerificationStatus.PENDING, initiated.upiVerificationStatus)
        assertFalse("Initiated settlement must NOT affect ledger", initiated.affectsLedger)

        // Verify friend ledger still has 1000 balance while QR is pending
        val txns = transactionRepository.getAllTransactionsList()
        val settlements = settlementRepository.getAllSettlementsList()
        val recon = LedgerCalculator.reconcileFriendLedger(friend, txns, settlements)
        assertEquals("Friend balance remains 1000 before payment", 100000L, recon.friend.netBalancePaise)
    }

    @Test
    fun test07_updatingPendingQrAmount_updatesSessionAndDoesNotAffectLedger() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Manan", upiId = "manan@upi"))
        val friend = friendRepository.getFriendById(friendId)!!

        val settlementId = upiSettlementManager.initiateQrSettlement(
            friend = friend,
            amountPaise = 30000L,
            isPayingFriend = false,
            qrReference = "RFQR1"
        )

        // User edits amount to 450
        val updated = upiSettlementManager.updatePendingSettlementAmount(
            settlementId = settlementId,
            newAmountPaise = 45000L,
            newQrReference = "RFQR2"
        )

        assertEquals("Updated amount is 450", 45000L, updated.amountPaise)
        assertEquals("Updated reference is RFQR2", "RFQR2", updated.referenceNumber)
        assertFalse("Still does not affect ledger", updated.affectsLedger)
    }

    // =========================================================================
    // SUITE 3: AUTOMATIC SMS RECONCILIATION (BUG 2)
    // =========================================================================

    @Test
    fun test08_incomingCreditSms_autoReconcilesPaymentRequestQr() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Manan", upiId = "manan@okhdfcbank"))
        val friend = friendRepository.getFriendById(friendId)!!

        // Friend owes 1000
        transactionRepository.saveTransaction(
            Transaction(
                title = "Dinner",
                amountPaise = 100000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 100000L))
            )
        )

        // User requests payment of 300 via QR
        val qrRef = "RFQR987654"
        val sId = upiSettlementManager.initiateQrSettlement(
            friend = friend,
            amountPaise = 30000L,
            isPayingFriend = false,
            qrReference = qrRef
        )

        // Incoming bank credit SMS
        val reconciled = upiSettlementManager.reconcileIncomingTransaction(
            amountPaise = 30000L,
            type = TransactionType.INCOME, // Credit
            referenceNumber = "UTR556677",
            upiReferenceNumber = "UTR556677",
            merchantOrPayee = "manan@okhdfcbank",
            senderUpiId = "manan@okhdfcbank",
            rawContent = "Your a/c credited by INR 300.00 from manan@okhdfcbank Ref UTR556677"
        )

        assertNotNull("Reconciliation must succeed", reconciled)
        assertEquals("Reconciled settlement ID matches", sId, reconciled!!.id)
        assertEquals("Status is SUCCESS", SettlementPaymentStatus.SUCCESS, reconciled.upiPaymentStatus)
        assertEquals("Status is AUTOMATIC_VERIFIED", SettlementVerificationStatus.AUTOMATIC_VERIFIED, reconciled.upiVerificationStatus)
        assertTrue("Now affects ledger", reconciled.affectsLedger)

        // Verify friend ledger balance updated immediately to 700 (1000 - 300)
        val txns = transactionRepository.getAllTransactionsList()
        val settlements = settlementRepository.getAllSettlementsList()
        val recon = LedgerCalculator.reconcileFriendLedger(friend, txns, settlements)
        assertEquals("Friend balance reduced to 700.00", 70000L, recon.friend.netBalancePaise)
        assertEquals("Outstanding total is 700.00", 70000L, recon.unsettledExpenses.sumOf { it.remainingOutstandingPaise })
    }

    @Test
    fun test09_incomingDebitSms_autoReconcilesUserPayingFriend() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Aman", upiId = "aman@paytm"))
        val friend = friendRepository.getFriendById(friendId)!!

        // User owes Aman 500
        transactionRepository.saveTransaction(
            Transaction(
                title = "Movie Tickets",
                amountPaise = 100000L,
                paidByFriendId = friendId, // Aman paid
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 50000L))
            )
        )

        // User initiates payment of 500 to Aman
        val sId = upiSettlementManager.initiateQrSettlement(
            friend = friend,
            amountPaise = 50000L,
            isPayingFriend = true,
            qrReference = "RFQR_PAY_500"
        )

        // Bank sends debit SMS
        val reconciled = upiSettlementManager.reconcileIncomingTransaction(
            amountPaise = 50000L,
            type = TransactionType.EXPENSE, // Debit
            referenceNumber = "99887766",
            merchantOrPayee = "aman@paytm",
            rawContent = "A/c debited INR 500.00 to aman@paytm UPI Ref 99887766"
        )

        assertNotNull("Reconciliation must succeed", reconciled)
        assertEquals("Status is SUCCESS", SettlementPaymentStatus.SUCCESS, reconciled!!.upiPaymentStatus)
        assertEquals("Status is AUTOMATIC_VERIFIED", SettlementVerificationStatus.AUTOMATIC_VERIFIED, reconciled.upiVerificationStatus)

        // Ledger balance is now settled
        val txns = transactionRepository.getAllTransactionsList()
        val settlements = settlementRepository.getAllSettlementsList()
        val recon = LedgerCalculator.reconcileFriendLedger(friend, txns, settlements)
        assertEquals("Net balance is now 0", 0L, recon.friend.netBalancePaise)
        assertTrue("Outstanding is empty", recon.unsettledExpenses.isEmpty())
    }

    @Test
    fun test10_wrongAmountOrPayee_doesNotReconcile() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Rahul", upiId = "rahul@upi"))
        val friend = friendRepository.getFriendById(friendId)!!

        upiSettlementManager.initiateQrSettlement(
            friend = friend,
            amountPaise = 30000L, // 300
            isPayingFriend = false,
            qrReference = "REF1"
        )

        // 1. Wrong amount (250 instead of 300)
        val res1 = upiSettlementManager.reconcileIncomingTransaction(
            amountPaise = 25000L,
            type = TransactionType.INCOME,
            merchantOrPayee = "rahul@upi",
            rawContent = "credited INR 250.00 from rahul@upi"
        )
        assertNull("Wrong amount must NOT reconcile", res1)

        // 2. Wrong direction (debit instead of credit for payment request)
        val res2 = upiSettlementManager.reconcileIncomingTransaction(
            amountPaise = 30000L,
            type = TransactionType.EXPENSE,
            merchantOrPayee = "rahul@upi",
            rawContent = "debited INR 300.00 to rahul@upi"
        )
        assertNull("Wrong direction must NOT reconcile", res2)
    }

    @Test
    fun test11_duplicateSms_doesNotReDuplicateSettlement() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Sameer", upiId = "sameer@upi"))
        val friend = friendRepository.getFriendById(friendId)!!

        val sId = upiSettlementManager.initiateQrSettlement(
            friend = friend,
            amountPaise = 20000L,
            isPayingFriend = false,
            qrReference = "REF_DUP"
        )

        // First SMS reconciles
        val firstMatch = upiSettlementManager.reconcileIncomingTransaction(
            amountPaise = 20000L,
            type = TransactionType.INCOME,
            referenceNumber = "UTR112233",
            merchantOrPayee = "sameer@upi",
            rawContent = "credited INR 200.00 from sameer@upi Ref UTR112233"
        )
        assertNotNull("First SMS reconciles", firstMatch)

        // Second duplicate SMS arrives: candidate is no longer INITIATED
        val secondMatch = upiSettlementManager.reconcileIncomingTransaction(
            amountPaise = 20000L,
            type = TransactionType.INCOME,
            referenceNumber = "UTR112233",
            merchantOrPayee = "sameer@upi",
            rawContent = "credited INR 200.00 from sameer@upi Ref UTR112233"
        )
        assertNull("Duplicate SMS must NOT re-reconcile or create another settlement", secondMatch)

        val allSettlements = settlementRepository.getAllSettlementsList()
        assertEquals("Must only have 1 settlement in database", 1, allSettlements.size)
    }
}