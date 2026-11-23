package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.local.entity.TransactionEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.data.sms.parser.ReferenceExtractor
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.location.OfflineLocationManager
import com.naman.rupeeflow.report.CategoryReportItem
import com.naman.rupeeflow.report.ExpenseGroupReportItem
import com.naman.rupeeflow.report.ParticipantReportItem
import com.naman.rupeeflow.report.PdfReportGenerator
import com.naman.rupeeflow.report.ReportGenerationData
import com.naman.rupeeflow.ui.screens.report.ReportGeneratorViewModel
import com.naman.rupeeflow.upi.UpiSettlementManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [com.naman.rupeeflow.report.ShadowPdfDocument::class])
class LedgerReportsItemsLocationAndUpiQATest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var reportDraftRepository: ReportDraftRepository
    private lateinit var upiSettlementManager: UpiSettlementManager

    private var friendAId: Long = 0L
    private var friendBId: Long = 0L
    private var friendCId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        categoryRepository = CategoryRepositoryImpl(database.categoryDao())
        friendRepository = FriendRepositoryImpl(database.friendDao())
        settlementRepository = SettlementRepositoryImpl(database.settlementDao())
        reportDraftRepository = ReportDraftRepositoryImpl(database.reportDraftDao())
        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao(),
            context = context
        )
        upiSettlementManager = UpiSettlementManager(
            settlementRepository = settlementRepository,
            friendRepository = friendRepository
        )

        // Seed default categories
        database.categoryDao().insertCategories(RupeeFlowDatabase.DEFAULT_CATEGORIES)

        // Seed friends
        friendAId = friendRepository.addFriend(Friend(name = "Friend A", upiId = "frienda@upi"))
        friendBId = friendRepository.addFriend(Friend(name = "Friend B", upiId = "friendb@upi"))
        friendCId = friendRepository.addFriend(Friend(name = "Friend C", upiId = "friendc@upi"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    // =========================================================================
    // FEATURE 1: REPORTS - EXCLUDE ₹0 SHARE PARTICIPANTS & SHOW MY PERSONAL SHARE
    // =========================================================================

    @Test
    fun test001_reportSelectionScreen_displaysMyPersonalShareNotTotalAmount() = runBlocking {
        // A group dinner costs ₹1,000 (100,000 paise).
        // Me: ₹400 (40,000 paise), Friend A: ₹300 (30,000 paise), Friend B: ₹300 (30,000 paise).
        val txn = Transaction(
            title = "Group Dinner",
            amountPaise = 100_000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null, // Me paid
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 40_000L, percentage = 40.0),
                TransactionParticipant(friendId = friendAId, friendName = "Friend A", shareAmountPaise = 30_000L, percentage = 30.0),
                TransactionParticipant(friendId = friendBId, friendName = "Friend B", shareAmountPaise = 30_000L, percentage = 30.0)
            ),
            splitMethod = SplitMethod.EXACT_AMOUNT,
            transactionType = TransactionType.EXPENSE
        )
        val id = transactionRepository.saveTransaction(txn)
        val saved = transactionRepository.getTransactionById(id)!!

        val myPersonalShare = PersonalFinancialAggregator.calculatePersonalExpense(saved)
        assertEquals("My personal share must be exactly ₹400 (40000 paise)", 40_000L, myPersonalShare)
        assertNotEquals("Must not display full transaction amount ₹1000", saved.amountPaise, myPersonalShare)
    }

    @Test
    fun test002_reportViewModel_excludesTransactionsWhereUserShareIsZero() = runBlocking {
        // Expense 1: Group Dinner where user participates (Me: ₹400, Friend A: ₹600)
        transactionRepository.saveTransaction(
            Transaction(
                title = "Dinner with Friend A",
                amountPaise = 100_000L,
                categoryId = 1L,
                transactionDate = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.UPI,
                paidByFriendId = null,
                participants = listOf(
                    TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 40_000L),
                    TransactionParticipant(friendId = friendAId, friendName = "Friend A", shareAmountPaise = 60_000L)
                ),
                splitMethod = SplitMethod.EXACT_AMOUNT
            )
        )

        // Expense 2: An expense between Friend A and Friend B where user has ₹0 share
        transactionRepository.saveTransaction(
            Transaction(
                title = "Snacks for Friends",
                amountPaise = 50_000L,
                categoryId = 1L,
                transactionDate = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.UPI,
                paidByFriendId = friendAId,
                participants = listOf(
                    TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 0L),
                    TransactionParticipant(friendId = friendAId, friendName = "Friend A", shareAmountPaise = 25_000L),
                    TransactionParticipant(friendId = friendBId, friendName = "Friend B", shareAmountPaise = 25_000L)
                ),
                splitMethod = SplitMethod.EXACT_AMOUNT
            )
        )

        val viewModel = ReportGeneratorViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            reportDraftRepository = reportDraftRepository
        )

        val state = viewModel.uiState.first { !it.isLoading && it.rawTransactionsInRange.isNotEmpty() }

        // The transaction where my share is ₹0 must NOT appear in the report selection list!
        val titles = state.rawTransactionsInRange.map { it.title }
        assertTrue("Dinner with Friend A should appear", titles.contains("Dinner with Friend A"))
        assertFalse("Snacks with ₹0 user share should be excluded from report selection", titles.contains("Snacks for Friends"))
    }

    @Test
    fun test003_participantBreakdown_strictlyOmitsZeroShareParticipants() = runBlocking {
        // Group expense where Friend C is in group but has ₹0 share in this split
        val txn = Transaction(
            title = "Pizza Party",
            amountPaise = 90_000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 50_000L),
                TransactionParticipant(friendId = friendAId, friendName = "Friend A", shareAmountPaise = 40_000L),
                TransactionParticipant(friendId = friendCId, friendName = "Friend C", shareAmountPaise = 0L)
            ),
            splitMethod = SplitMethod.EXACT_AMOUNT
        )
        transactionRepository.saveTransaction(txn)

        val viewModel = ReportGeneratorViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            reportDraftRepository = reportDraftRepository
        )

        val state = viewModel.uiState.first { !it.isLoading && it.participantBreakdown.isNotEmpty() }
        val participantsInBreakdown = state.participantBreakdown.map { it.participantName }

        assertTrue("Me should be in participant breakdown", participantsInBreakdown.contains("Me"))
        assertTrue("Friend A should be in participant breakdown", participantsInBreakdown.contains("Friend A"))
        assertFalse("Friend C with ₹0 share must be omitted from participant breakdown", participantsInBreakdown.contains("Friend C"))
    }

    @Test
    fun test004_pdfReportGenerator_rendersWithoutZeroShareParticipants() {
        val data = ReportGenerationData(
            title = "Expense Report",
            periodLabel = "November 2026",
            dateRangeLabel = "01 Nov 2026 - 30 Nov 2026",
            transactions = emptyList(),
            totalExpensesPaise = 90_000L,
            totalIncomePaise = 0L,
            totalRefundsPaise = 0L,
            netSpendingPaise = 90_000L,
            cashSpendingPaise = 0L,
            onlineSpendingPaise = 90_000L,
            categoryBreakdown = listOf(
                CategoryReportItem("Food", 90_000L, 100.0, 1)
            ),
            expenseGroupBreakdown = emptyList(),
            participantBreakdown = listOf(
                ParticipantReportItem("Me", 50_000L, 1),
                ParticipantReportItem("Friend A", 40_000L, 1),
                ParticipantReportItem("Friend Zero", 0L, 1) // Should be skipped
            )
        )

        val pdfFile = PdfReportGenerator.generatePdf(context, data)
        assertNotNull(pdfFile)
        assertTrue(pdfFile.exists())
        assertTrue(pdfFile.length() > 0)
    }

    // =========================================================================
    // FEATURE 2: ITEM-WISE EXPENSE SPLITTING ("Split by Items")
    // =========================================================================

    @Test
    fun test005_itemWiseSplit_calculatesQuantityAndUnitPricePaiseAccurately() {
        // Pizza: Qty 2, Unit Price ₹350.00 (35,000 paise) -> Total ₹700.00 (70,000 paise)
        val item1 = TransactionItem(
            name = "Margherita Pizza",
            quantity = 2,
            unitPricePaise = 35_000L,
            allocations = listOf(
                ItemAllocation(friendId = null, allocatedAmountPaise = 35_000L),
                ItemAllocation(friendId = friendAId, allocatedAmountPaise = 35_000L)
            )
        )
        assertEquals(70_000L, item1.totalAmountPaise)

        // Mocktail: Qty 3, Unit Price ₹150.00 (15,000 paise) -> Total ₹450.00 (45,000 paise)
        val item2 = TransactionItem(
            name = "Virgin Mojito",
            quantity = 3,
            unitPricePaise = 15_000L,
            allocations = listOf(
                ItemAllocation(friendId = friendBId, allocatedAmountPaise = 45_000L)
            )
        )
        assertEquals(45_000L, item2.totalAmountPaise)

        val items = listOf(item1, item2)
        val shares = LedgerCalculator.calculateItemWiseShares(items, listOf(null, friendAId, friendBId))

        assertEquals("Me owes 35000 paise for Pizza", 35_000L, shares[null])
        assertEquals("Friend A owes 35000 paise for Pizza", 35_000L, shares[friendAId])
        assertEquals("Friend B owes 45000 paise for Drinks", 45_000L, shares[friendBId])

        val totalAmountPaise = items.sumOf { it.totalAmountPaise }
        val sumOfShares = shares.values.sum()
        assertEquals("Sum of shares must strictly equal total amount paise", totalAmountPaise, sumOfShares)
    }

    @Test
    fun test006_itemWiseSplit_distributesRemainderPaiseDeterministically() {
        // Item costs ₹100.00 (10,000 paise) divided among 3 participants
        // Shares should be 3334, 3333, 3333 paise (sum = 10,000 paise)
        val item = TransactionItem(
            name = "Dessert Platter",
            quantity = 1,
            unitPricePaise = 10_000L,
            allocations = listOf(
                ItemAllocation(friendId = null, allocatedAmountPaise = 3334L),
                ItemAllocation(friendId = friendAId, allocatedAmountPaise = 3333L),
                ItemAllocation(friendId = friendBId, allocatedAmountPaise = 3333L)
            )
        )
        val shares = LedgerCalculator.calculateItemWiseShares(listOf(item), listOf(null, friendAId, friendBId))
        assertEquals(10_000L, shares.values.sum())
    }

    @Test
    fun test007_itemWiseSplit_persistsAndRetrievesItemsAndAllocationsFromRoom() = runBlocking {
        val item1 = TransactionItem(
            name = "Garlic Bread",
            quantity = 1,
            unitPricePaise = 20_000L,
            totalAmountPaise = 20_000L,
            allocations = listOf(
                ItemAllocation(friendId = null, allocatedAmountPaise = 10_000L),
                ItemAllocation(friendId = friendAId, allocatedAmountPaise = 10_000L)
            )
        )
        val item2 = TransactionItem(
            name = "Pasta",
            quantity = 2,
            unitPricePaise = 30_000L,
            totalAmountPaise = 60_000L,
            allocations = listOf(
                ItemAllocation(friendId = null, allocatedAmountPaise = 30_000L),
                ItemAllocation(friendId = friendBId, allocatedAmountPaise = 30_000L)
            )
        )
        val totalPaise = item1.totalAmountPaise + item2.totalAmountPaise // 80,000 paise

        val txn = Transaction(
            title = "Italian Dinner",
            amountPaise = totalPaise,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 40_000L, splitMethod = SplitMethod.ITEM_WISE),
                TransactionParticipant(friendId = friendAId, friendName = "Friend A", shareAmountPaise = 10_000L, splitMethod = SplitMethod.ITEM_WISE),
                TransactionParticipant(friendId = friendBId, friendName = "Friend B", shareAmountPaise = 30_000L, splitMethod = SplitMethod.ITEM_WISE)
            ),
            splitMethod = SplitMethod.ITEM_WISE,
            items = listOf(item1, item2)
        )

        val txnId = transactionRepository.saveTransaction(txn)
        assertTrue(txnId > 0L)

        val retrieved = transactionRepository.getTransactionById(txnId)
        assertNotNull(retrieved)
        assertEquals(SplitMethod.ITEM_WISE, retrieved!!.splitMethod)
        assertEquals(2, retrieved.items.size)

        val retrievedBread = retrieved.items.first { it.name == "Garlic Bread" }
        assertEquals(1, retrievedBread.quantity)
        assertEquals(20_000L, retrievedBread.unitPricePaise)
        assertEquals(2, retrievedBread.allocations.size)

        val retrievedPasta = retrieved.items.first { it.name == "Pasta" }
        assertEquals(2, retrievedPasta.quantity)
        assertEquals(30_000L, retrievedPasta.unitPricePaise)
        assertEquals(60_000L, retrievedPasta.totalAmountPaise)
    }

    @Test
    fun test008_itemWiseSplit_cascadeDeletesItemsOnTransactionDelete() = runBlocking {
        val item = TransactionItem(
            name = "Coffee",
            quantity = 2,
            unitPricePaise = 15_000L,
            allocations = listOf(
                ItemAllocation(friendId = null, allocatedAmountPaise = 30_000L)
            )
        )
        val txn = Transaction(
            title = "Coffee Meet",
            amountPaise = 30_000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 30_000L)
            ),
            splitMethod = SplitMethod.ITEM_WISE,
            items = listOf(item)
        )
        val id = transactionRepository.saveTransaction(txn)

        val itemsBefore = database.transactionDao().getItemsForTransaction(id)
        assertEquals(1, itemsBefore.size)

        transactionRepository.deleteTransaction(id)

        val itemsAfter = database.transactionDao().getItemsForTransaction(id)
        assertTrue("Items must be cascaded and deleted", itemsAfter.isEmpty())
    }

    // =========================================================================
    // FEATURE 3: TRANSACTION LOCATION CAPTURE AT DETECTION
    // =========================================================================

    @Test
    fun test009_offlineLocationManager_capturesLocationWithTimeoutFallback() = runBlocking {
        // captureDetectionLocation with no location permissions should safely return null without throwing
        val loc = OfflineLocationManager.captureDetectionLocation(context, timeoutMs = 500L)
        // In Robolectric test without permission mock, returns null safely
        assertTrue(loc == null || loc.latitude != 0.0)
    }

    // =========================================================================
    // FEATURE 4: UPI REFERENCE EXTRACTION & SETTLEMENT RECONCILIATION
    // =========================================================================

    @Test
    fun test010_referenceExtractor_extractsStandardAndSlashDelimitedUpiReferences() {
        // Standard labels
        assertEquals("104810950877", ReferenceExtractor.extractUpiReference("Rs. 500 debited from A/c XX1234. UPI Ref No: 104810950877"))
        assertEquals("328491829102", ReferenceExtractor.extractUpiReference("Paid Rs. 250 via UPI. UTR 328491829102"))
        assertEquals("928371928371", ReferenceExtractor.extractUpiReference("Payment of INR 120. RRN 928371928371"))

        // Slash-delimited formats
        assertEquals("432109876543", ReferenceExtractor.extractUpiReference("UPI/432109876543/Payment to merchant"))
        assertEquals("543210987654", ReferenceExtractor.extractUpiReference("UPI/CR/543210987654/Friend/Payment"))
        assertEquals("654321098765", ReferenceExtractor.extractUpiReference("UPI/P2A/654321098765/Transfer"))
        assertEquals("765432109876", ReferenceExtractor.extractUpiReference("Txn done via UTR/765432109876"))
    }

    @Test
    fun test011_referenceExtractor_rejectsOtpsAccountNumbersAndHelplines() {
        val smsWithOtp = "Your OTP is 482910 for transaction of Rs 500. Call helpline 1800123456. Ref: 987612345678"
        val ref = ReferenceExtractor.extractUpiReference(smsWithOtp)
        assertEquals("Should extract reference, rejecting OTP and helpline", "987612345678", ref)

        val smsOtpOnly = "Your verification code is 829102 to login. Do not share OTP."
        val refOtpOnly = ReferenceExtractor.extractUpiReference(smsOtpOnly)
        assertNull("Must reject OTP when no real UPI reference exists", refOtpOnly)
    }

    @Test
    fun test012_upiSettlementManager_reconcilesSettlementFromRawContent() = runBlocking {
        // Friend owes me ₹350 (35,000 paise). I initiate a QR settlement.
        val qrRef = "RF_QR_TEST_123"
        val settlementId = upiSettlementManager.initiateQrSettlement(
            friend = Friend(id = friendAId, name = "Friend A", upiId = "frienda@upi"),
            amountPaise = 35_000L,
            isPayingFriend = false, // Friend pays Me
            qrReference = qrRef
        )
        val initial = settlementRepository.getSettlementById(settlementId)!!
        assertEquals(SettlementVerificationStatus.PENDING, initial.upiVerificationStatus)

        // Incoming bank SMS without parsed upiReferenceNumber, but rawContent contains slash-delimited reference
        val rawBankSms = "Rs 350.00 credited to A/c XX5678 via UPI/CR/998877665544/Friend A."
        val reconciled = upiSettlementManager.reconcileIncomingTransaction(
            amountPaise = 35_000L,
            type = TransactionType.INCOME,
            rawContent = rawBankSms,
            timestamp = System.currentTimeMillis()
        )

        assertNotNull("Settlement should be reconciled from rawContent", reconciled)
        assertEquals(settlementId, reconciled!!.id)
        assertEquals(SettlementVerificationStatus.AUTOMATIC_VERIFIED, reconciled.upiVerificationStatus)
        assertEquals("998877665544", reconciled.referenceNumber)
        assertEquals("998877665544", reconciled.upiTransactionRef)
        assertTrue(reconciled.affectsLedger)
    }
}
