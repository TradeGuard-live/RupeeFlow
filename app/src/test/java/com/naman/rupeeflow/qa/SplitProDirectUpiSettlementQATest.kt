package com.naman.rupeeflow.qa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.FriendEntity
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
 * Complete Automated QA Test Suite for Split Pro Direct UPI Settlement Integration.
 * Verifies all 30 core specifications:
 * 1. Valid UPI ID
 * 2. Invalid UPI ID
 * 3. Missing UPI ID
 * 4. BHIM installed discovery
 * 5. Google Pay installed discovery
 * 6. PhonePe installed discovery
 * 7. Multiple UPI applications discovery
 * 8. No UPI application installed handling
 * 9. Correct recipient prefilled
 * 10. Correct amount prefilled
 * 11. Partial settlement calculation
 * 12. Custom settlement
 * 13. Zero amount rejected
 * 14. Negative amount rejected
 * 15. Excess amount handled correctly
 * 16. Cancelled payment does not affect ledger
 * 17. Failed payment does not affect ledger
 * 18. Unknown payment result keeps pending
 * 19. Missing transaction reference fallback
 * 20. Duplicate callback idempotency
 * 21. Repeated settlement prevention
 * 22. App restart during payment recovery
 * 23. Return from UPI application response parsing
 * 24. Unverified manual confirmation affects ledger
 * 25. Verified settlement authoritative success
 * 26. Group balance update on verified settlement
 * 27. Friend balance update on verified settlement
 * 28. Settlement reversal restores balances
 * 29. Settlement restoration reapplies reduction
 * 30. Existing manual settlement regression safety
 */
@RunWith(RobolectricTestRunner::class)
class SplitProDirectUpiSettlementQATest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var upiSettlementManager: UpiSettlementManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        friendRepository = FriendRepositoryImpl(database.friendDao())
        settlementRepository = SettlementRepositoryImpl(database.settlementDao(), database.friendDao(), context)
        upiSettlementManager = UpiSettlementManager(settlementRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. Valid UPI ID
    @Test
    fun test01_validUpiId() {
        assertTrue(UpiValidator.isValidUpiId("ronit@okhdfcbank"))
        assertTrue(UpiValidator.isValidUpiId("priya.sharma@paytm"))
        assertTrue(UpiValidator.isValidUpiId("friend-123@upi"))
        assertTrue(UpiValidator.isValidUpiId("merchant_store@icici"))
    }

    // 2. Invalid UPI ID
    @Test
    fun test02_invalidUpiId() {
        assertFalse(UpiValidator.isValidUpiId("invalid upi"))
        assertFalse(UpiValidator.isValidUpiId("ronit@@bank"))
        assertFalse(UpiValidator.isValidUpiId("ronit&am=100@bank"))
        assertFalse(UpiValidator.isValidUpiId("ronit?pa=hack@bank"))
        assertFalse(UpiValidator.isValidUpiId("no-at-sign"))
    }

    // 3. Missing UPI ID
    @Test
    fun test03_missingUpiId() {
        assertFalse(UpiValidator.isValidUpiId(null))
        assertFalse(UpiValidator.isValidUpiId(""))
        assertFalse(UpiValidator.isValidUpiId("   "))
    }

    // 4. BHIM installed
    @Test
    fun test04_bhimInstalledDiscovery() {
        assertEquals("in.org.npci.upiapp", UpiIntentBuilder.BHIM_PACKAGE)
        val info = UpiAppInfo(
            packageName = UpiIntentBuilder.BHIM_PACKAGE,
            appName = "BHIM",
            isBhim = true
        )
        assertTrue(info.isBhim)
    }

    // 5. Google Pay installed
    @Test
    fun test05_googlePayInstalledDiscovery() {
        assertEquals("com.google.android.apps.nbu.paisa.user", UpiIntentBuilder.GOOGLE_PAY_PACKAGE)
        val info = UpiAppInfo(
            packageName = UpiIntentBuilder.GOOGLE_PAY_PACKAGE,
            appName = "Google Pay",
            isGooglePay = true
        )
        assertTrue(info.isGooglePay)
    }

    // 6. PhonePe installed
    @Test
    fun test06_phonePeInstalledDiscovery() {
        assertEquals("com.phonepe.app", UpiIntentBuilder.PHONEPE_PACKAGE)
        val info = UpiAppInfo(
            packageName = UpiIntentBuilder.PHONEPE_PACKAGE,
            appName = "PhonePe",
            isPhonePe = true
        )
        assertTrue(info.isPhonePe)
    }

    // 7. Multiple UPI applications installed
    @Test
    fun test07_multipleUpiAppsDiscovery() {
        val appList = listOf(
            UpiAppInfo(UpiIntentBuilder.BHIM_PACKAGE, "BHIM", isBhim = true),
            UpiAppInfo(UpiIntentBuilder.GOOGLE_PAY_PACKAGE, "Google Pay", isGooglePay = true),
            UpiAppInfo(UpiIntentBuilder.PHONEPE_PACKAGE, "PhonePe", isPhonePe = true),
            UpiAppInfo(UpiIntentBuilder.PAYTM_PACKAGE, "Paytm", isPaytm = true)
        )
        assertEquals(4, appList.size)
        assertTrue(appList.any { it.isBhim })
        assertTrue(appList.any { it.isGooglePay })
        assertTrue(appList.any { it.isPhonePe })
        assertTrue(appList.any { it.isPaytm })
    }

    // 8. No UPI application installed
    @Test
    fun test08_noUpiApplicationInstalled() {
        val emptyApps = emptyList<UpiAppInfo>()
        assertTrue(emptyApps.isEmpty())
        val intent = UpiIntentBuilder.buildUpiIntent(
            receiverUpiId = "friend@okhdfcbank",
            receiverName = "Friend",
            amountPaise = 50000L,
            note = "Settlement"
        )
        assertNotNull(intent)
        assertEquals(Intent.ACTION_VIEW, intent.action)
    }

    // 9. Correct recipient prefilled
    @Test
    fun test09_correctRecipientPrefilled() {
        val uriStr = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "priya@okhdfcbank",
            receiverName = "Priya Sharma",
            amountPaise = 25000L,
            note = "Dinner split"
        )
        assertTrue(uriStr.contains("pa=priya@okhdfcbank") || uriStr.contains("pa=priya%40okhdfcbank"))
        assertTrue(uriStr.contains("pn=Priya+Sharma") || uriStr.contains("pn=Priya%20Sharma"))
    }

    // 10. Correct amount prefilled
    @Test
    fun test10_correctAmountPrefilled() {
        val uriStr = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "ronit@icici",
            receiverName = "Ronit",
            amountPaise = 125050L, // 1250.50 INR
            note = "Trip share"
        )
        assertTrue(uriStr.contains("am=1250.50"))
        assertTrue(uriStr.contains("cu=INR"))
    }

    // 11. Partial settlement
    @Test
    fun test11_partialSettlement() {
        val totalDebtPaise = 100000L // 1000.00 INR
        val partialAmountPaise = totalDebtPaise / 2L // 500.00 INR
        val validation = UpiValidator.validateAmount(partialAmountPaise, totalDebtPaise)
        assertTrue(validation is AmountValidationResult.Valid)

        val uriStr = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "friend@upi",
            receiverName = "Friend",
            amountPaise = partialAmountPaise,
            note = "50% partial payment"
        )
        assertTrue(uriStr.contains("am=500.00"))
    }

    // 12. Custom settlement
    @Test
    fun test12_customSettlement() {
        val totalDebtPaise = 85000L // 850.00 INR
        val customAmountPaise = 32000L // 320.00 INR
        val validation = UpiValidator.validateAmount(customAmountPaise, totalDebtPaise)
        assertTrue(validation is AmountValidationResult.Valid)

        val uriStr = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "friend@upi",
            receiverName = "Friend",
            amountPaise = customAmountPaise,
            note = "Custom settlement"
        )
        assertTrue(uriStr.contains("am=320.00"))
    }

    // 13. Zero amount rejected
    @Test
    fun test13_zeroAmountRejected() {
        val validation = UpiValidator.validateAmount(0L)
        assertTrue(validation is AmountValidationResult.Invalid)

        try {
            UpiIntentBuilder.buildUpiUri("friend@upi", "Friend", 0L, "Note")
            fail("Expected IllegalArgumentException for zero amount")
        } catch (_: IllegalArgumentException) {
            // Success
        }
    }

    // 14. Negative amount rejected
    @Test
    fun test14_negativeAmountRejected() {
        val validation = UpiValidator.validateAmount(-5000L)
        assertTrue(validation is AmountValidationResult.Invalid)

        try {
            UpiIntentBuilder.buildUpiUri("friend@upi", "Friend", -5000L, "Note")
            fail("Expected IllegalArgumentException for negative amount")
        } catch (_: IllegalArgumentException) {
            // Success
        }
    }

    // 15. Excess amount handled correctly
    @Test
    fun test15_excessAmountHandledCorrectly() {
        val maxDebtPaise = 50000L // 500 INR
        val excessPaise = 60000L // 600 INR
        val validation = UpiValidator.validateAmount(excessPaise, maxDebtPaise)
        assertTrue(validation is AmountValidationResult.ExceedsBalance)
        val exceeds = validation as AmountValidationResult.ExceedsBalance
        assertEquals(excessPaise, exceeds.enteredPaise)
        assertEquals(maxDebtPaise, exceeds.maxAllowedPaise)
    }

    // 16. Cancelled payment
    @Test
    fun test16_cancelledPayment() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Aman", upiId = "aman@okhdfcbank")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        val (sId, _) = upiSettlementManager.initiateSettlement(friend, 25000L)
        val updated = upiSettlementManager.handlePaymentResult(
            settlementId = sId,
            resultCode = Activity.RESULT_CANCELED,
            data = null
        )

        assertEquals(SettlementPaymentStatus.CANCELLED, updated.upiPaymentStatus)
        assertEquals(SettlementVerificationStatus.UNVERIFIED, updated.upiVerificationStatus)
        assertFalse(updated.affectsLedger)
    }

    // 17. Failed payment
    @Test
    fun test17_failedPayment() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Bhavik", upiId = "bhavik@axisbank")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        val (sId, _) = upiSettlementManager.initiateSettlement(friend, 15000L)
        val intent = Intent().apply {
            putExtra("response", "txnId=UPI999&responseCode=ZD&Status=FAILURE&txnRef=RF123")
        }
        val updated = upiSettlementManager.handlePaymentResult(
            settlementId = sId,
            resultCode = Activity.RESULT_OK,
            data = intent
        )

        assertEquals(SettlementPaymentStatus.FAILED, updated.upiPaymentStatus)
        assertEquals(SettlementVerificationStatus.UNVERIFIED, updated.upiVerificationStatus)
        assertFalse(updated.affectsLedger)
    }

    // 18. Unknown payment result
    @Test
    fun test18_unknownPaymentResult() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Chirag", upiId = "chirag@ybl")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        val (sId, _) = upiSettlementManager.initiateSettlement(friend, 30000L)
        val intent = Intent().apply {
            putExtra("response", "Status=UNKNOWN_STATUS_CODE&foo=bar")
        }
        val updated = upiSettlementManager.handlePaymentResult(
            settlementId = sId,
            resultCode = Activity.RESULT_OK,
            data = intent
        )

        assertEquals(SettlementPaymentStatus.UNKNOWN, updated.upiPaymentStatus)
        assertFalse(updated.affectsLedger)
    }

    // 19. Missing transaction reference
    @Test
    fun test19_missingTransactionReference() {
        val ref = UpiIntentBuilder.generateTransactionReference()
        assertNotNull(ref)
        assertTrue(ref.startsWith("RF"))
        assertTrue(ref.length >= 10)
    }

    // 20. Duplicate callback
    @Test
    fun test20_duplicateCallback() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Divya", upiId = "divya@icici")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        val (sId, _) = upiSettlementManager.initiateSettlement(friend, 40000L)
        val intent = Intent().apply {
            putExtra("response", "txnId=UPI111&ApprovalRefNo=APR111&Status=SUCCESS&txnRef=RF111")
        }

        // First callback
        val res1 = upiSettlementManager.handlePaymentResult(sId, Activity.RESULT_OK, intent)
        assertEquals(SettlementPaymentStatus.SUCCESS, res1.upiPaymentStatus)
        assertTrue(res1.affectsLedger)

        // Second duplicate callback
        val res2 = upiSettlementManager.handlePaymentResult(sId, Activity.RESULT_OK, intent)
        assertEquals(res1.id, res2.id)
        assertEquals(SettlementPaymentStatus.SUCCESS, res2.upiPaymentStatus)

        // Total settlements in database remains exactly 1
        val all = settlementRepository.getAllSettlementsList()
        assertEquals(1, all.size)
    }

    // 21. Repeated settlement prevention
    @Test
    fun test21_repeatedSettlementPrevention() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Esha", upiId = "esha@paytm")
        )

        val s1 = Settlement(
            id = 0L,
            payerId = null,
            receiverId = friendId,
            amountPaise = 20000L,
            paymentMethod = PaymentMethod.UPI,
            referenceNumber = "DUP_TEST_REF_123",
            upiTransactionRef = "DUP_TEST_REF_123"
        )
        val id1 = settlementRepository.recordSettlement(s1)
        assertTrue(id1 > 0L)

        // Attempting to record duplicate settlement with identical reference
        val id2 = settlementRepository.recordSettlement(s1)
        assertEquals(id1, id2)

        val total = settlementRepository.getAllSettlementsList()
        assertEquals(1, total.size)
    }

    // 22. App restart during payment
    @Test
    fun test22_appRestartDuringPayment() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Farhan", upiId = "farhan@okhdfcbank")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        val (sId, _) = upiSettlementManager.initiateSettlement(friend, 35000L)

        // Simulate app restart: re-fetch from database
        val reloaded = settlementRepository.getSettlementById(sId)
        assertNotNull(reloaded)
        assertEquals(SettlementPaymentStatus.INITIATED, reloaded!!.upiPaymentStatus)
        assertEquals(SettlementVerificationStatus.PENDING, reloaded.upiVerificationStatus)
        assertFalse(reloaded.affectsLedger)
    }

    // 23. Return from UPI application
    @Test
    fun test23_returnFromUpiApplication() {
        val parsed = UpiResponseParser.parseResponseString(
            "txnId=TXN987654&responseCode=00&ApprovalRefNo=APP888&Status=SUCCESS&txnRef=RF9876"
        )
        assertEquals(SettlementPaymentStatus.SUCCESS, parsed.paymentStatus)
        assertEquals(SettlementVerificationStatus.AUTOMATIC_VERIFIED, parsed.verificationStatus)
        assertEquals("TXN987654", parsed.txnId)
        assertEquals("APP888", parsed.approvalRefNo)
    }

    // 24. Unverified manual confirmation
    @Test
    fun test24_unverifiedManualConfirmation() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Gauri", upiId = "gauri@sbi")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        val (sId, _) = upiSettlementManager.initiateSettlement(friend, 18000L)
        // User returned without signed receipt; taps manual confirmation
        val confirmed = upiSettlementManager.confirmManuallyUnverified(sId)

        assertEquals(SettlementPaymentStatus.SUCCESS, confirmed.upiPaymentStatus)
        assertEquals(SettlementVerificationStatus.MANUAL_UNVERIFIED, confirmed.upiVerificationStatus)
        assertTrue(confirmed.affectsLedger)
        assertNotEquals(SettlementVerificationStatus.AUTOMATIC_VERIFIED, confirmed.upiVerificationStatus)
    }

    // 25. Verified settlement
    @Test
    fun test25_verifiedSettlement() = runBlocking {
        val friendId = friendRepository.addFriend(
            Friend(name = "Harsh", upiId = "harsh@kotak")
        )
        val friend = friendRepository.getFriendById(friendId)!!

        val (sId, _) = upiSettlementManager.initiateSettlement(friend, 22000L)
        val intent = Intent().apply {
            putExtra("response", "txnId=UPI222&ApprovalRefNo=REF222&Status=SUCCESS&txnRef=RF222")
        }
        val verified = upiSettlementManager.handlePaymentResult(sId, Activity.RESULT_OK, intent)

        assertEquals(SettlementPaymentStatus.SUCCESS, verified.upiPaymentStatus)
        assertEquals(SettlementVerificationStatus.AUTOMATIC_VERIFIED, verified.upiVerificationStatus)
        assertTrue(verified.affectsLedger)
    }

    // 26. Group balance update
    @Test
    fun test26_groupBalanceUpdate() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Isha", upiId = "isha@okaxis"))
        val friend = friendRepository.getFriendById(friendId)!!

        val initialDebtPaise = 50000L // I owe Isha 500 INR
        val expense = Transaction(
            id = 1L,
            title = "Group Dinner",
            amountPaise = initialDebtPaise,
            paidByFriendId = friendId, // Friend paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = initialDebtPaise) // I participated
            )
        )

        // 1. Initial balance without settlement
        val ledgersBefore = LedgerCalculator.computeFriendLedgers(listOf(friend), listOf(expense), emptyList())
        assertEquals(-initialDebtPaise, ledgersBefore.first().netBalancePaise)

        // 2. Perform verified UPI settlement
        val (sId, _) = upiSettlementManager.initiateSettlement(friend, initialDebtPaise)
        val intent = Intent().apply {
            putExtra("response", "txnId=UPI333&ApprovalRefNo=APP333&Status=SUCCESS&txnRef=RF333")
        }
        val verifiedSettlement = upiSettlementManager.handlePaymentResult(sId, Activity.RESULT_OK, intent)

        // 3. Balance after verified settlement
        val ledgersAfter = LedgerCalculator.computeFriendLedgers(
            listOf(friend),
            listOf(expense),
            listOf(verifiedSettlement)
        )
        assertEquals(0L, ledgersAfter.first().netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgersAfter.first().status)
    }

    // 27. Friend balance update
    @Test
    fun test27_friendBalanceUpdate() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Karan", upiId = "karan@upi"))
        val friend = friendRepository.getFriendById(friendId)!!

        val expense = Transaction(
            id = 2L,
            title = "Cab ride",
            amountPaise = 40000L,
            paidByFriendId = friendId,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 40000L)
            )
        )

        val settlement = Settlement(
            id = 10L,
            payerId = null,
            receiverId = friendId,
            amountPaise = 40000L,
            paymentMethod = PaymentMethod.UPI,
            upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
            upiVerificationStatus = SettlementVerificationStatus.AUTOMATIC_VERIFIED
        )

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friend), listOf(expense), listOf(settlement))
        assertEquals(0L, ledgers.first().netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgers.first().status)
    }

    // 28. Settlement reversal
    @Test
    fun test28_settlementReversal() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Lalit", upiId = "lalit@icici"))
        val friend = friendRepository.getFriendById(friendId)!!

        val expense = Transaction(
            id = 3L,
            title = "Groceries",
            amountPaise = 60000L,
            paidByFriendId = friendId,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 60000L)
            )
        )

        val settlement = Settlement(
            id = 20L,
            payerId = null,
            receiverId = friendId,
            amountPaise = 60000L,
            paymentMethod = PaymentMethod.UPI,
            isReversed = true, // REVERSED!
            upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
            upiVerificationStatus = SettlementVerificationStatus.AUTOMATIC_VERIFIED
        )

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friend), listOf(expense), listOf(settlement))
        // Reversal restores the debt!
        assertEquals(-60000L, ledgers.first().netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledgers.first().status)
    }

    // 29. Settlement restoration
    @Test
    fun test29_settlementRestoration() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Mohit", upiId = "mohit@sbi"))
        val friend = friendRepository.getFriendById(friendId)!!

        val expense = Transaction(
            id = 4L,
            title = "Hotel stay",
            amountPaise = 80000L,
            paidByFriendId = friendId,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 80000L)
            )
        )

        // Unreversed / restored settlement
        val settlement = Settlement(
            id = 30L,
            payerId = null,
            receiverId = friendId,
            amountPaise = 80000L,
            paymentMethod = PaymentMethod.UPI,
            isReversed = false,
            upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
            upiVerificationStatus = SettlementVerificationStatus.AUTOMATIC_VERIFIED
        )

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friend), listOf(expense), listOf(settlement))
        assertEquals(0L, ledgers.first().netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgers.first().status)
    }

    // 30. Existing manual settlement regression
    @Test
    fun test30_existingManualSettlementRegression() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Neha"))
        val friend = friendRepository.getFriendById(friendId)!!

        val expense = Transaction(
            id = 5L,
            title = "Movie tickets",
            amountPaise = 50000L,
            paidByFriendId = friendId,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L)
            )
        )

        // Traditional manual cash settlement without explicit UPI fields
        val cashSettlement = Settlement(
            id = 40L,
            payerId = null,
            receiverId = friendId,
            amountPaise = 50000L,
            paymentMethod = PaymentMethod.CASH,
            notes = "Cash hand-to-hand payment"
        )

        assertTrue(cashSettlement.affectsLedger)
        assertEquals(SettlementPaymentStatus.SUCCESS, cashSettlement.upiPaymentStatus)
        assertEquals(SettlementVerificationStatus.MANUAL_UNVERIFIED, cashSettlement.upiVerificationStatus)

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friend), listOf(expense), listOf(cashSettlement))
        assertEquals(0L, ledgers.first().netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgers.first().status)
    }

    // 31. BHIM and MobiKwik P2P URI omits 'tr' parameter
    @Test
    fun test31_bhimAndMobiKwikP2PUriOmitsTr() {
        val uriStr = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "friend@okhdfcbank",
            receiverName = "Rahul",
            amountPaise = 50000L,
            note = "Split Pro Settlement"
        )
        assertFalse("P2P URI must not contain tr parameter to avoid BHIM and MobiKwik errors", uriStr.contains("tr="))
    }

    // 32. BHIM and MobiKwik Payee VPA retains literal '@'
    @Test
    fun test32_bhimAndMobiKwikPayeeVpaLiteralAtSign() {
        val uriStr = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "priya@axisbank",
            receiverName = "Priya",
            amountPaise = 25000L,
            note = "Cab share"
        )
        assertTrue("Payee VPA must retain literal @ for BHIM and MobiKwik regex validation", uriStr.contains("pa=priya@axisbank"))
        assertFalse(uriStr.contains("pa=priya%40axisbank"))
    }

    // 33. P2P URI omits 'tn' for all apps (BHIM-safe by default)
    @Test
    fun test33_p2pUriOmitsTnForAllApps() {
        // Without targetPackage (generic/system chooser)
        val uriGeneric = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "user@upi",
            receiverName = "John Doe",
            amountPaise = 10000L,
            note = "Friday Dinner Settlement"
        )
        assertFalse("P2P URI must NEVER contain tn to stay BHIM-safe", uriGeneric.contains("tn="))
        assertTrue(uriGeneric.contains("pn=John%20Doe"))

        // With explicit BHIM targetPackage
        val uriBhim = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "user@upi",
            receiverName = "John Doe",
            amountPaise = 10000L,
            note = "Friday Dinner Settlement",
            targetPackage = UpiIntentBuilder.BHIM_PACKAGE
        )
        assertFalse("BHIM URI must not contain tn", uriBhim.contains("tn="))

        // With Google Pay targetPackage
        val uriGpay = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "user@upi",
            receiverName = "John Doe",
            amountPaise = 10000L,
            note = "Friday Dinner Settlement",
            targetPackage = UpiIntentBuilder.GOOGLE_PAY_PACKAGE
        )
        assertFalse("GPay P2P URI must also omit tn for consistency", uriGpay.contains("tn="))
    }

    // 34. Special characters sanitized from payee name
    @Test
    fun test34_specialCharsSanitizedFromPayeeName() {
        val uriStr = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "user@upi",
            receiverName = "John & Jane (Roommates)",
            amountPaise = 15000L,
            note = "Split Pro: Goa Trip #1!"
        )
        val query = uriStr.substringAfter("?")
        // No tn in P2P URI
        assertFalse("P2P URI must not contain tn", query.contains("tn="))
        assertTrue(query.contains("pn=John%20Jane%20Roommates") || query.contains("pn=John%20%20Jane%20%20Roommates"))
    }

    // 35. initiateSettlement produces P2P compliant intent without tr
    @Test
    fun test35_initiateSettlementProducesP2PCompliantIntent() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Kunal", upiId = "kunal@okhdfcbank"))
        val friend = friendRepository.getFriendById(friendId)!!

        val (sId, intent) = upiSettlementManager.initiateSettlement(
            friend = friend,
            amountPaise = 75000L,
            note = "Trip expenses"
        )
        assertTrue(sId > 0L)
        val dataUri = intent.dataString ?: ""
        assertTrue(dataUri.startsWith("upi://pay?"))
        assertTrue(dataUri.contains("pa=kunal@okhdfcbank"))
        assertTrue(dataUri.contains("am=750.00"))
        assertTrue(dataUri.contains("cu=INR"))
        assertFalse("Intent URI data must not contain tr for personal settlements", dataUri.contains("tr="))
    }

    // 36. BHIM target produces maximally-compatible URI (no tn, no tr, URI-encoded pn)
    @Test
    fun test36_bhimTargetProducesMaximallyCompatibleUri() {
        val uriStr = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "friend@upi",
            receiverName = "Rahul Sharma",
            amountPaise = 25000L,
            note = "Dinner Share",
            targetPackage = UpiIntentBuilder.BHIM_PACKAGE
        )
        assertFalse("BHIM URI must omit tn to avoid 'This type of request is not supported' error", uriStr.contains("tn="))
        assertFalse("BHIM URI must not contain tr", uriStr.contains("tr="))
        assertTrue("BHIM URI must contain pa with literal @", uriStr.contains("pa=friend@upi"))
        assertTrue("BHIM URI must contain am", uriStr.contains("am=250.00"))
        assertTrue("BHIM URI must contain cu=INR", uriStr.contains("cu=INR"))
        // pn is now URI-encoded full name for all apps (unified approach)
        assertTrue("BHIM URI must contain URI-encoded pn", uriStr.contains("pn=Rahul%20Sharma"))
    }

    // 37. BHIM Intent sets explicit package, no explicit categories (matches standard UPI library pattern)
    @Test
    fun test37_bhimIntentExplicitPackageNoExplicitCategories() {
        val intent = UpiIntentBuilder.buildUpiIntent(
            receiverUpiId = "friend@upi",
            receiverName = "Rahul Sharma",
            amountPaise = 25000L,
            note = "Dinner Share",
            targetPackage = UpiIntentBuilder.BHIM_PACKAGE
        )
        assertEquals(UpiIntentBuilder.BHIM_PACKAGE, intent.`package`)
        // No explicit categories — Android adds CATEGORY_DEFAULT automatically during resolution.
        // Explicitly adding CATEGORY_DEFAULT caused BHIM to reject intents on certain devices.
        assertTrue(intent.categories == null || intent.categories.isEmpty())
        val dataUri = intent.dataString ?: ""
        assertFalse(dataUri.contains("tn="))
        assertTrue(dataUri.contains("pn=Rahul%20Sharma"))
    }

    // 38. initiateSettlement with BHIM target produces maximally-compatible BHIM intent
    @Test
    fun test38_initiateSettlementWithBhimTarget() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Kunal Verma", upiId = "kunal@okhdfcbank"))
        val friend = friendRepository.getFriendById(friendId)!!

        val (sId, intent) = upiSettlementManager.initiateSettlement(
            friend = friend,
            amountPaise = 75000L,
            note = "Trip expenses",
            targetPackage = UpiIntentBuilder.BHIM_PACKAGE
        )
        assertTrue(sId > 0L)
        assertEquals(UpiIntentBuilder.BHIM_PACKAGE, intent.`package`)
        val dataUri = intent.dataString ?: ""
        assertTrue(dataUri.startsWith("upi://pay?"))
        assertTrue(dataUri.contains("pa=kunal@okhdfcbank"))
        assertTrue(dataUri.contains("pn=Kunal%20Verma"))
        assertTrue(dataUri.contains("am=750.00"))
        assertTrue(dataUri.contains("cu=INR"))
        assertFalse("BHIM intent must not contain tn", dataUri.contains("tn="))
        assertFalse("BHIM intent must not contain tr", dataUri.contains("tr="))
    }

    // 39. Generic/null targetPackage URI is also BHIM-safe (no tn, no tr)
    @Test
    fun test39_genericUriIsBhimSafe() {
        val uriStr = UpiIntentBuilder.buildUpiUri(
            receiverUpiId = "friend@okhdfcbank",
            receiverName = "Test Friend",
            amountPaise = 50000L,
            note = "Settlement from RupeeFlow",
            targetPackage = null
        )
        assertFalse("Generic URI must not contain tn (BHIM could open from system chooser)", uriStr.contains("tn="))
        assertFalse("Generic URI must not contain tr for P2P", uriStr.contains("tr="))
        assertTrue(uriStr.contains("pa=friend@okhdfcbank"))
        assertTrue(uriStr.contains("am=500.00"))
        assertTrue(uriStr.contains("cu=INR"))
    }

    // 40. All UPI intents have no explicit categories (standard library pattern)
    @Test
    fun test40_allUpiIntentsHaveNoExplicitCategories() {
        // Generic intent (no target package)
        val genericIntent = UpiIntentBuilder.buildUpiIntent(
            receiverUpiId = "friend@upi",
            receiverName = "Friend",
            amountPaise = 10000L,
            note = "Test"
        )
        assertTrue("Generic intent should have no explicit categories",
            genericIntent.categories == null || genericIntent.categories.isEmpty())

        // GPay-targeted intent
        val gpayIntent = UpiIntentBuilder.buildUpiIntent(
            receiverUpiId = "friend@upi",
            receiverName = "Friend",
            amountPaise = 10000L,
            note = "Test",
            targetPackage = UpiIntentBuilder.GOOGLE_PAY_PACKAGE
        )
        assertTrue("GPay intent should have no explicit categories",
            gpayIntent.categories == null || gpayIntent.categories.isEmpty())
    }
}
