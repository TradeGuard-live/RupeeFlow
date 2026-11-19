package com.naman.rupeeflow.qa

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.upi.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Automated QA Test Suite for RupeeFlow Personal UPI Settlement QR.
 *
 * Validates all 10 core scenarios:
 * 1. Friend owes ₹9.30 (exact two decimals formatting).
 * 2. Friend owes ₹500.00.
 * 3. Friend owes ₹0.00 (QR generation disabled / rejected).
 * 4. Partially settled expense (QR generates only for remaining outstanding debt).
 * 5. Multiple outstanding expenses (QR aggregates net debt).
 * 6. Reversed settlement (re-opens outstanding debt for QR generation).
 * 7. Missing or invalid personal UPI ID validation.
 * 8. QR bitmap generation, sharing, and saving locally.
 * 9. URI parameter parsing verifying receiver VPA, name, amount, currency, reference.
 * 10. Confirm that generating or sharing a QR never changes ledger balances.
 */
@RunWith(RobolectricTestRunner::class)
class PersonalUpiSettlementQrQATest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var friendRepository: FriendRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var merchantPreferenceRepository: MerchantPreferenceRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        merchantPreferenceRepository = MerchantPreferenceRepositoryImpl(database.inputUsageDao())
        friendRepository = FriendRepositoryImpl(database.friendDao())
        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao(),
            context = context,
            merchantPreferenceRepository = merchantPreferenceRepository
        )
        settlementRepository = SettlementRepositoryImpl(
            settlementDao = database.settlementDao(),
            friendDao = database.friendDao(),
            context = context
        )
        userPreferencesRepository = UserPreferencesRepository(context)

        runBlocking {
            database.categoryDao().insertCategory(
                com.naman.rupeeflow.data.local.entity.CategoryEntity(
                    id = 1L,
                    name = "Food & Dining",
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

    // 1. Friend owes ₹9.30 - URI contains exact amount with two decimal places
    @Test
    fun test01_friendOwes9_30_generatesExactFormattedUri() {
        val amountPaise = 930L // ₹9.30
        val uriStr = UpiQrCodeGenerator.buildSettlementQrUri(
            receiverUpiId = "myupi@okhdfcbank",
            receiverName = "Naman",
            amountPaise = amountPaise,
            note = "Split Pro Settlement",
            transactionRef = "RFQR12345"
        )

        assertTrue("URI must start with upi://pay?", uriStr.startsWith("upi://pay?"))
        assertTrue("URI must contain pa with literal @", uriStr.contains("pa=myupi@okhdfcbank"))
        assertTrue("URI must contain am=9.30", uriStr.contains("am=9.30"))
        assertTrue("URI must contain cu=INR", uriStr.contains("cu=INR"))
        assertTrue("URI must contain URL-encoded pn", uriStr.contains("pn=Naman"))
        assertTrue("URI must contain URL-encoded tn", uriStr.contains("tn=Split%20Pro%20Settlement"))
        assertTrue("URI must contain tr", uriStr.contains("tr=RFQR12345"))
        assertFalse("URI must NOT contain mam parameter", uriStr.contains("mam="))
    }

    // 2. Friend owes ₹500.00 - URI formats am=500.00
    @Test
    fun test02_friendOwes500_generatesExactFormattedUri() {
        val amountPaise = 50000L // ₹500.00
        val uriStr = UpiQrCodeGenerator.buildSettlementQrUri(
            receiverUpiId = "user@axisbank",
            receiverName = "Rahul Sharma",
            amountPaise = amountPaise,
            note = "Dinner share",
            transactionRef = "RFQR99999"
        )

        assertTrue(uriStr.contains("pa=user@axisbank"))
        assertTrue(uriStr.contains("am=500.00"))
        assertTrue(uriStr.contains("pn=Rahul%20Sharma"))
        assertTrue(uriStr.contains("cu=INR"))
        assertFalse(uriStr.contains("mam="))
    }

    // 3. Friend owes ₹0.00 - QR generation must be disabled and throw exception
    @Test
    fun test03_friendOwesZero_qrGenerationDisabled() {
        try {
            UpiQrCodeGenerator.buildSettlementQrUri(
                receiverUpiId = "user@upi",
                receiverName = "User",
                amountPaise = 0L,
                note = "Zero balance"
            )
            fail("buildSettlementQrUri must throw IllegalArgumentException for 0L amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("greater than zero") == true)
        }

        try {
            UpiQrCodeGenerator.buildSettlementQrUri(
                receiverUpiId = "user@upi",
                receiverName = "User",
                amountPaise = -1500L,
                note = "Negative balance"
            )
            fail("buildSettlementQrUri must throw IllegalArgumentException for negative amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("greater than zero") == true)
        }
    }

    // 4. Partially settled expense - QR generates only for remaining outstanding debt
    @Test
    fun test04_partiallySettledExpense_qrCalculatesExactRemainingOutstanding() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Amit", upiId = "amit@upi"))

        // Expense of ₹100.00 (10000 paise): User paid ₹100, Amit owes ₹50 (5000 paise)
        val txn = Transaction(
            id = 0L,
            title = "Lunch",
            amountPaise = 10000L,
            categoryId = 1L,
            transactionType = TransactionType.EXPENSE,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = null, // User paid
            splitMethod = SplitMethod.EQUAL,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 5000L),
                TransactionParticipant(friendId = friendId, shareAmountPaise = 5000L)
            )
        )
        transactionRepository.saveTransaction(txn)

        // Amit partially settles ₹30.00 (3000 paise)
        val settlement = Settlement(
            id = 0L,
            payerId = friendId,
            receiverId = null, // ME
            amountPaise = 3000L,
            upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
            upiVerificationStatus = SettlementVerificationStatus.MANUAL_UNVERIFIED
        )
        settlementRepository.recordSettlement(settlement)

        val txns = transactionRepository.getAllTransactions().first()
        val settlements = settlementRepository.getAllSettlements().first()
        val friends = friendRepository.getAllFriends().first()
        val ledgers = LedgerCalculator.computeFriendLedgers(friends, txns, settlements)
        val amit = ledgers.first { it.id == friendId }

        assertEquals(FriendBalanceStatus.THEY_OWE_ME, amit.status)
        assertEquals(2000L, amit.netBalancePaise) // ₹20.00 remaining

        val qrUri = UpiQrCodeGenerator.buildSettlementQrUri(
            receiverUpiId = "naman@upi",
            receiverName = "Naman",
            amountPaise = amit.netBalancePaise
        )

        assertTrue("QR must request remaining ₹20.00, not original ₹50.00", qrUri.contains("am=20.00"))
        assertFalse(qrUri.contains("am=50.00"))
        assertFalse(qrUri.contains("am=100.00"))
    }

    // 5. Multiple outstanding expenses - QR aggregates total outstanding debt
    @Test
    fun test05_multipleOutstandingExpenses_qrAggregatesTotalOutstandingDebt() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Suresh", upiId = "suresh@upi"))

        // Expense 1: ₹100 owed
        transactionRepository.saveTransaction(
            Transaction(
                id = 0L,
                title = "Dinner",
                amountPaise = 20000L,
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null,
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 10000L),
                    TransactionParticipant(friendId = friendId, shareAmountPaise = 10000L) // 10000 paise
                )
            )
        )

        // Expense 2: ₹50 owed
        transactionRepository.saveTransaction(
            Transaction(
                id = 0L,
                title = "Taxi",
                amountPaise = 10000L,
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 2000L,
                paidByFriendId = null,
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 5000L),
                    TransactionParticipant(friendId = friendId, shareAmountPaise = 5000L) // 5000 paise
                )
            )
        )

        // Expense 3: ₹25 owed
        transactionRepository.saveTransaction(
            Transaction(
                id = 0L,
                title = "Coffee",
                amountPaise = 5000L,
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 3000L,
                paidByFriendId = null,
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 2500L),
                    TransactionParticipant(friendId = friendId, shareAmountPaise = 2500L) // 2500 paise
                )
            )
        )

        val txns = transactionRepository.getAllTransactions().first()
        val settlements = settlementRepository.getAllSettlements().first()
        val friends = friendRepository.getAllFriends().first()
        val ledgers = LedgerCalculator.computeFriendLedgers(friends, txns, settlements)
        val suresh = ledgers.first { it.id == friendId }

        assertEquals(17500L, suresh.netBalancePaise) // ₹175.00

        val qrUri = UpiQrCodeGenerator.buildSettlementQrUri(
            receiverUpiId = "user@bank",
            receiverName = "User",
            amountPaise = suresh.netBalancePaise
        )

        assertTrue("QR must request total aggregate debt ₹175.00", qrUri.contains("am=175.00"))
    }

    // 6. Reversed settlement - restores outstanding balance for QR generation
    @Test
    fun test06_reversedSettlement_restoresOutstandingBalanceForQr() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Priya", upiId = "priya@upi"))

        // Expense where Priya owes ₹150.00 (15000 paise)
        transactionRepository.saveTransaction(
            Transaction(
                id = 0L,
                title = "Groceries",
                amountPaise = 30000L,
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null,
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 15000L),
                    TransactionParticipant(friendId = friendId, shareAmountPaise = 15000L)
                )
            )
        )

        // Record settlement of ₹150.00
        val settlementId = settlementRepository.recordSettlement(
            Settlement(
                id = 0L,
                payerId = friendId,
                receiverId = null,
                amountPaise = 15000L,
                upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
                upiVerificationStatus = SettlementVerificationStatus.MANUAL_UNVERIFIED
            )
        )

        var txns = transactionRepository.getAllTransactions().first()
        var settlements = settlementRepository.getAllSettlements().first()
        var friends = friendRepository.getAllFriends().first()
        var ledgers = LedgerCalculator.computeFriendLedgers(friends, txns, settlements)
        var priya = ledgers.first { it.id == friendId }
        assertEquals(FriendBalanceStatus.SETTLED, priya.status)
        assertEquals(0L, priya.netBalancePaise)

        // Reverse the settlement
        settlementRepository.reverseSettlement(settlementId)

        txns = transactionRepository.getAllTransactions().first()
        settlements = settlementRepository.getAllSettlements().first()
        friends = friendRepository.getAllFriends().first()
        ledgers = LedgerCalculator.computeFriendLedgers(friends, txns, settlements)
        priya = ledgers.first { it.id == friendId }

        assertEquals(FriendBalanceStatus.THEY_OWE_ME, priya.status)
        assertEquals(15000L, priya.netBalancePaise) // Restored back to ₹150.00

        val qrUri = UpiQrCodeGenerator.buildSettlementQrUri(
            receiverUpiId = "user@upi",
            receiverName = "User",
            amountPaise = priya.netBalancePaise
        )
        assertTrue("Restored balance generates QR for ₹150.00", qrUri.contains("am=150.00"))
    }

    // 7. Missing or invalid personal UPI ID validation
    @Test
    fun test07_missingOrInvalidPersonalUpiId_validation() {
        assertFalse(UpiValidator.isValidUpiId(null))
        assertFalse(UpiValidator.isValidUpiId(""))
        assertFalse(UpiValidator.isValidUpiId("   "))
        assertFalse(UpiValidator.isValidUpiId("plainusername"))
        assertFalse(UpiValidator.isValidUpiId("@bankhandle"))
        assertFalse(UpiValidator.isValidUpiId("user@"))
        assertFalse(UpiValidator.isValidUpiId("user@bank&am=100"))
        assertFalse(UpiValidator.isValidUpiId("user name@bank"))

        assertTrue(UpiValidator.isValidUpiId("user@okhdfcbank"))
        assertTrue(UpiValidator.isValidUpiId("9876543210@paytm"))
        assertTrue(UpiValidator.isValidUpiId("john.doe@axisbank"))
        assertTrue(UpiValidator.isValidUpiId("rahul_123@ybl"))

        try {
            UpiQrCodeGenerator.buildSettlementQrUri(
                receiverUpiId = "invalid_upi_id",
                receiverName = "Name",
                amountPaise = 5000L
            )
            fail("Must throw for invalid receiver UPI ID")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid receiver UPI ID") == true)
        }
    }

    // 8. QR bitmap generation and local integrity
    @Test
    fun test08_qrBitmapGenerationAndLocalIntegrity() {
        val uriStr = UpiQrCodeGenerator.buildSettlementQrUri(
            receiverUpiId = "user@okhdfcbank",
            receiverName = "Naman",
            amountPaise = 50000L,
            note = "Friday Dinner",
            transactionRef = "RFQRTEST001"
        )

        val bitmap = UpiQrCodeGenerator.generateQrBitmap(uriStr, 256)
        assertNotNull(bitmap)
        assertEquals(256, bitmap.width)
        assertEquals(256, bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }

    // 9. Scan using supported UPI apps - parameter verification
    @Test
    fun test09_scanUsingSupportedUpiApps_parameterVerification() {
        val amountPaise = 12550L // ₹125.50
        val uriStr = UpiQrCodeGenerator.buildSettlementQrUri(
            receiverUpiId = "john@icici",
            receiverName = "John Doe",
            amountPaise = amountPaise,
            note = "Movie share",
            transactionRef = "RFQR88888"
        )

        val uri = Uri.parse(uriStr)
        assertEquals("upi", uri.scheme)
        assertEquals("pay", uri.host)
        assertEquals("john@icici", uri.getQueryParameter("pa"))
        assertEquals("John Doe", uri.getQueryParameter("pn"))
        assertEquals("125.50", uri.getQueryParameter("am"))
        assertEquals("INR", uri.getQueryParameter("cu"))
        assertEquals("Movie share", uri.getQueryParameter("tn"))
        assertEquals("RFQR88888", uri.getQueryParameter("tr"))
        assertNull("mam must never be present", uri.getQueryParameter("mam"))
    }

    // 10. Confirm that QR generation never changes ledger balances
    @Test
    fun test10_qrGenerationNeverChangesLedgerBalances() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Kavita", upiId = "kavita@upi"))

        transactionRepository.saveTransaction(
            Transaction(
                id = 0L,
                title = "Snacks",
                amountPaise = 8000L,
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null,
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 4000L),
                    TransactionParticipant(friendId = friendId, shareAmountPaise = 4000L)
                )
            )
        )

        var txns = transactionRepository.getAllTransactions().first()
        var settlements = settlementRepository.getAllSettlements().first()
        var friends = friendRepository.getAllFriends().first()
        val balanceBefore = LedgerCalculator.computeFriendLedgers(friends, txns, settlements).first().netBalancePaise

        assertEquals(4000L, balanceBefore)

        // Generate multiple QRs
        val qr1 = UpiQrCodeGenerator.buildSettlementQrUri("user@upi", "User", balanceBefore)
        val qr2 = UpiQrCodeGenerator.buildSettlementQrUri("user@upi", "User", balanceBefore)
        val bmp = UpiQrCodeGenerator.generateQrBitmap(qr1, 512)

        assertNotNull(bmp)
        assertNotNull(qr1)
        assertNotNull(qr2)

        // Ledger balance after QR generation MUST remain exactly identical
        txns = transactionRepository.getAllTransactions().first()
        settlements = settlementRepository.getAllSettlements().first()
        friends = friendRepository.getAllFriends().first()
        val balanceAfter = LedgerCalculator.computeFriendLedgers(friends, txns, settlements).first().netBalancePaise

        assertEquals(balanceBefore, balanceAfter)
        assertEquals(0, settlements.size) // No settlement was inserted
    }

    // 11. Manual confirmation "Mark as received (not bank verified)" records settlement
    @Test
    fun test11_markAsReceivedRecordsSettlementAndPreservesHistory() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Rohan", upiId = "rohan@upi"))

        transactionRepository.saveTransaction(
            Transaction(
                id = 0L,
                title = "Dinner",
                amountPaise = 6000L,
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null,
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 3000L),
                    TransactionParticipant(friendId = friendId, shareAmountPaise = 3000L)
                )
            )
        )

        var txns = transactionRepository.getAllTransactions().first()
        var settlements = settlementRepository.getAllSettlements().first()
        var friends = friendRepository.getAllFriends().first()
        var rohan = LedgerCalculator.computeFriendLedgers(friends, txns, settlements).first()
        assertEquals(3000L, rohan.netBalancePaise)

        // User taps "Mark as received (not bank verified)"
        val recordedSettlement = Settlement(
            id = 0L,
            payerId = friendId,
            receiverId = null, // ME
            amountPaise = 3000L,
            settlementDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            referenceNumber = "RFQRROHAN01",
            notes = "Personal UPI QR Settlement from Rohan",
            upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
            upiVerificationStatus = SettlementVerificationStatus.MANUAL_UNVERIFIED,
            upiTransactionRef = "RFQRROHAN01",
            upiReceiverVpa = "my@upi"
        )
        settlementRepository.recordSettlement(recordedSettlement)

        txns = transactionRepository.getAllTransactions().first()
        settlements = settlementRepository.getAllSettlements().first()
        friends = friendRepository.getAllFriends().first()
        rohan = LedgerCalculator.computeFriendLedgers(friends, txns, settlements).first()

        assertEquals(FriendBalanceStatus.SETTLED, rohan.status)
        assertEquals(0L, rohan.netBalancePaise)

        // History check: complete expense history remains available and marked as fully settled
        val history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, settlements)
        assertEquals(1, history.size)
        assertTrue(history.first().isFullySettled)
        assertEquals(0L, history.first().remainingOutstandingPaise)
        assertEquals(3000L, history.first().settledAmountPaise)
    }

    // 12. Personal UPI configuration saving and clearing in UserPreferencesRepository
    @Test
    fun test12_userPreferencesPersonalUpiPersistence() = runBlocking {
        userPreferencesRepository.setPersonalUpiDetails("personal@okhdfcbank", "My Display Name")

        var prefs = userPreferencesRepository.userPreferencesFlow.first()
        assertEquals("personal@okhdfcbank", prefs.personalUpiId)
        assertEquals("My Display Name", prefs.personalUpiName)

        // Clear details
        userPreferencesRepository.setPersonalUpiDetails(null, null)
        prefs = userPreferencesRepository.userPreferencesFlow.first()
        assertNull(prefs.personalUpiId)
        assertNull(prefs.personalUpiName)
    }
}
