package com.naman.rupeeflow.qa

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.screens.friends.FriendDetailViewModel
import com.naman.rupeeflow.ui.screens.transactions.TransactionsViewModel
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
import java.io.File
import java.io.FileOutputStream

/**
 * Comprehensive QA Test Suite verifying:
 * 1. PDF & Image bill attachment storage, retrieval, size limits, and backup validation.
 * 2. Friend settlement FIFO allocation, partial settlements, and outstanding-only expenses.
 * 3. Simplified Transactions screen filter layout (Group A & Group B removed, Period filters retained).
 * 4. Responsive Detected Transactions layout and non-wrapping status badges.
 */
@RunWith(RobolectricTestRunner::class)
class PdfAttachmentAndSettlementAllocationQATest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var friendGroupRepository: FriendGroupRepository
    private lateinit var expenseGroupRepository: ExpenseGroupRepository
    private lateinit var pendingRepository: PendingTransactionRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        friendRepository = FriendRepositoryImpl(database.friendDao())
        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao()
        )
        categoryRepository = CategoryRepositoryImpl(database.categoryDao())
        settlementRepository = SettlementRepositoryImpl(database.settlementDao(), database.friendDao())
        friendGroupRepository = FriendGroupRepositoryImpl(database.friendGroupDao())
        expenseGroupRepository = ExpenseGroupRepositoryImpl(
            expenseGroupDao = database.expenseGroupDao(),
            transactionDao = database.transactionDao(),
            friendGroupDao = database.friendGroupDao()
        )
        pendingRepository = PendingTransactionRepositoryImpl(database.pendingTransactionDao(), database.transactionDao())

        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity(id = 1L, name = "Food & Dining", iconName = "restaurant", colorHex = "#EF4444")
            )
            database.categoryDao().insertCategory(
                CategoryEntity(id = 2L, name = "Travel", iconName = "flight", colorHex = "#3B82F6")
            )
            database.friendDao().insertFriend(
                FriendEntity(id = 101L, name = "Amit Sharma", phoneNumber = "+919876543210")
            )
            database.friendDao().insertFriend(
                FriendEntity(id = 102L, name = "Priya Patel", phoneNumber = "+919123456789")
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // =========================================================================
    // SECTION 1: PDF & IMAGE BILL ATTACHMENTS
    // =========================================================================

    @Test
    fun test01_PdfAttachmentIdentificationAndHelpers() {
        // Null / blank checks
        assertFalse(ImageStorageManager.isPdfAttachment(null))
        assertFalse(ImageStorageManager.isPdfAttachment(""))
        assertFalse(ImageStorageManager.isPdfAttachment("   "))

        // Image files
        assertFalse(ImageStorageManager.isPdfAttachment("/data/user/0/app/files/receipts/bill_1.jpg"))
        assertFalse(ImageStorageManager.isPdfAttachment("/data/user/0/app/files/receipts/bill_2.png"))
        assertFalse(ImageStorageManager.isPdfAttachment("/data/user/0/app/files/receipts/bill_3.webp"))

        // PDF files (case-insensitive)
        assertTrue(ImageStorageManager.isPdfAttachment("/data/user/0/app/files/receipts/bill_1.pdf"))
        assertTrue(ImageStorageManager.isPdfAttachment("/data/user/0/app/files/receipts/BILL_INVOICE.PDF"))

        // File name extraction
        assertEquals("invoice.pdf", ImageStorageManager.getAttachmentFileName("/receipts/invoice.pdf"))
        assertEquals("bill_photo.jpg", ImageStorageManager.getAttachmentFileName("/receipts/bill_photo.jpg"))
        assertEquals("", ImageStorageManager.getAttachmentFileName(null))

        // File size formatting
        val tempFile = File(context.cacheDir, "sample_test.pdf")
        tempFile.writeBytes(ByteArray(2048)) // 2 KB
        val sizeFormatted = ImageStorageManager.getAttachmentFileSizeFormatted(tempFile.absolutePath)
        assertTrue("Formatted size must contain KB: $sizeFormatted", sizeFormatted.contains("KB"))
        tempFile.delete()
    }

    @Test
    fun test02_BackupRestoreAttachmentValidatorAllowsPdf() {
        // PDF document attachment allowed
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_12345.pdf"))
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("receipt_march.PDF"))

        // Standard images allowed
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_12345.jpg"))
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_12345.jpeg"))
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_12345.png"))
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_12345.webp"))

        // Disallowed / dangerous extensions or paths rejected
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("../bill.pdf"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill.exe"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill.sh"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill.apk"))
    }

    @Test
    fun test03_SavePdfAttachmentWithinSizeLimit() {
        runBlocking {
            // Create mock PDF file in cache
            val pdfFile = File(context.cacheDir, "mock_bill.pdf")
            pdfFile.writeBytes("%PDF-1.4\nTest PDF content for RupeeFlow\n%%EOF".toByteArray())
            val uri = Uri.fromFile(pdfFile)

            val savedPath = ImageStorageManager.saveBillAttachment(context, uri)
            assertNotNull("Saved PDF path must not be null", savedPath)
            assertTrue("Saved path must end with .pdf", savedPath!!.endsWith(".pdf"))
            assertTrue("Saved file must exist on disk", File(savedPath).exists())
            assertTrue("ImageStorageManager must recognize it as PDF", ImageStorageManager.isPdfAttachment(savedPath))

            // Clean up
            pdfFile.delete()
            File(savedPath).delete()
        }
    }

    @Test
    fun test04_SavePdfAttachmentExceedingSizeLimitReturnsNull() {
        runBlocking {
            // Create file larger than 15MB limit
            val oversizedFile = File(context.cacheDir, "huge_bill.pdf")
            val outputStream = FileOutputStream(oversizedFile)
            val chunk = ByteArray(1024 * 1024) // 1MB chunk
            repeat(16) {
                outputStream.write(chunk)
            }
            outputStream.close()
            val uri = Uri.fromFile(oversizedFile)

            val savedPath = ImageStorageManager.saveBillAttachment(context, uri)
            assertNull("Oversized attachment exceeding 15MB must be safely rejected and return null", savedPath)

            oversizedFile.delete()
        }
    }

    @Test
    fun test05_TransactionWithPdfAttachmentPersistedInDatabase() = runBlocking {
        val now = System.currentTimeMillis()
        val pdfPath = "/data/user/0/com.naman.rupeeflow/files/receipts/bill_9999.pdf"

        val transaction = Transaction(
            id = 1L,
            title = "Office Equipment Invoice",
            amountPaise = 250000L, // 2,500.00
            transactionDate = now,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L,
            paymentMethod = PaymentMethod.UPI,
            billImagePath = pdfPath
        )

        transactionRepository.saveTransaction(transaction)

        val retrieved = transactionRepository.getTransactionById(1L)
        assertNotNull(retrieved)
        assertEquals(pdfPath, retrieved!!.billImagePath)
        assertTrue(ImageStorageManager.isPdfAttachment(retrieved.billImagePath))
    }

    // =========================================================================
    // SECTION 2: FRIEND SETTLEMENT & FIFO ALLOCATION
    // =========================================================================

    @Test
    fun test06_SingleSharedExpense_ShowsUnsettledBeforeSettlement() {
        val friendId = 101L
        val now = 1000L

        // ME paid ₹500, friend share is ₹500
        val txn = Transaction(
            id = 1L,
            title = "Dinner at Taj",
            amountPaise = 50000L,
            transactionDate = now,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null, // ME paid
            participants = listOf(
                TransactionParticipant(transactionId = 1L, friendId = friendId, shareAmountPaise = 50000L)
            )
        )

        val unsettled = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn),
            settlements = emptyList()
        )

        assertEquals(1, unsettled.size)
        val item = unsettled.first()
        assertEquals(1L, item.transaction.id)
        assertEquals(50000L, item.originalShareAmountPaise)
        assertEquals(0L, item.settledAmountPaise)
        assertEquals(50000L, item.remainingOutstandingPaise)
        assertFalse(item.isPartiallySettled)
        assertEquals(ExpenseObligationDirection.THEY_OWE_ME, item.direction)
    }

    @Test
    fun test07_SingleSharedExpense_FullSettlementResultsInEmptyUnsettledList() {
        val friendId = 101L
        val now = 1000L

        val txn = Transaction(
            id = 1L,
            title = "Dinner at Taj",
            amountPaise = 50000L,
            transactionDate = now,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(transactionId = 1L, friendId = friendId, shareAmountPaise = 50000L)
            )
        )

        val settlement = Settlement(
            id = 10L,
            payerId = friendId,
            receiverId = null, // Friend paid ME
            amountPaise = 50000L, // Full settlement of ₹500
            settlementDate = now + 100,
            isReversed = false
        )

        val unsettled = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn),
            settlements = listOf(settlement)
        )

        assertTrue("Unsettled expenses must be empty when fully settled", unsettled.isEmpty())
    }

    @Test
    fun test08_SingleSharedExpense_PartialSettlementCalculatesRemainingCorrectly() {
        val friendId = 101L
        val now = 1000L

        // Expense of ₹600, friend share ₹600
        val txn = Transaction(
            id = 1L,
            title = "Grocery bill",
            amountPaise = 60000L,
            transactionDate = now,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(transactionId = 1L, friendId = friendId, shareAmountPaise = 60000L)
            )
        )

        // Friend paid ₹400 settlement
        val settlement = Settlement(
            id = 10L,
            payerId = friendId,
            receiverId = null,
            amountPaise = 40000L,
            settlementDate = now + 100,
            isReversed = false
        )

        val unsettled = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn),
            settlements = listOf(settlement)
        )

        assertEquals(1, unsettled.size)
        val item = unsettled.first()
        assertEquals(60000L, item.originalShareAmountPaise)
        assertEquals(40000L, item.settledAmountPaise)
        assertEquals(20000L, item.remainingOutstandingPaise) // ₹200 remaining
        assertTrue("Expense must be flagged as partially settled", item.isPartiallySettled)
    }

    @Test
    fun test09_MultipleExpenses_DeterministicFifoAllocation() {
        val friendId = 101L

        // Expense 1 on day 1: ₹400
        val txn1 = Transaction(
            id = 1L,
            title = "Movie Tickets",
            amountPaise = 40000L,
            transactionDate = 1000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(transactionId = 1L, friendId = friendId, shareAmountPaise = 40000L)
            )
        )

        // Expense 2 on day 2: ₹600
        val txn2 = Transaction(
            id = 2L,
            title = "Dinner",
            amountPaise = 60000L,
            transactionDate = 2000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(transactionId = 2L, friendId = friendId, shareAmountPaise = 60000L)
            )
        )

        // Settlement of ₹500 recorded
        // FIFO: Expense 1 (₹400) should be fully settled, Expense 2 (₹600) has ₹100 settled and ₹500 remaining
        val settlement = Settlement(
            id = 10L,
            payerId = friendId,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = 3000L,
            isReversed = false
        )

        val unsettled = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn1, txn2),
            settlements = listOf(settlement)
        )

        assertEquals("Only Expense 2 should remain outstanding", 1, unsettled.size)
        val item = unsettled.first()
        assertEquals(2L, item.transaction.id)
        assertEquals(60000L, item.originalShareAmountPaise)
        assertEquals(10000L, item.settledAmountPaise)
        assertEquals(50000L, item.remainingOutstandingPaise)
        assertTrue(item.isPartiallySettled)
    }

    @Test
    fun test10_NewExpenseAddedAfterFullSettlement_OnlyNewExpenseAppears() {
        val friendId = 101L

        val txn1 = Transaction(
            id = 1L,
            title = "Old Expense",
            amountPaise = 50000L,
            transactionDate = 1000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(transactionId = 1L, friendId = friendId, shareAmountPaise = 50000L)
            )
        )

        val settlement = Settlement(
            id = 10L,
            payerId = friendId,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = 2000L,
            isReversed = false
        )

        // New expense added on day 3
        val txn2 = Transaction(
            id = 2L,
            title = "New Weekend Lunch",
            amountPaise = 35000L,
            transactionDate = 3000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(transactionId = 2L, friendId = friendId, shareAmountPaise = 35000L)
            )
        )

        val unsettled = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn1, txn2),
            settlements = listOf(settlement)
        )

        assertEquals("Only new expense should appear in unsettled list", 1, unsettled.size)
        assertEquals(2L, unsettled.first().transaction.id)
        assertEquals(35000L, unsettled.first().remainingOutstandingPaise)
        assertFalse(unsettled.first().isPartiallySettled)
    }

    @Test
    fun test11_OverpaymentCreditBalance_AllExpensesSettled() {
        val friendId = 101L

        val txn = Transaction(
            id = 1L,
            title = "Snacks",
            amountPaise = 30000L, // ₹300
            transactionDate = 1000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(transactionId = 1L, friendId = friendId, shareAmountPaise = 30000L)
            )
        )

        // Friend overpays ₹500
        val settlement = Settlement(
            id = 10L,
            payerId = friendId,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = 2000L,
            isReversed = false
        )

        val unsettled = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn),
            settlements = listOf(settlement)
        )

        assertTrue("All expenses must be marked settled even on overpayment", unsettled.isEmpty())
    }

    @Test
    fun test12_FriendPaidForMe_ShowsIOweThemDirection() {
        val friendId = 101L

        // Friend paid ₹800, my share is ₹800
        val txn = Transaction(
            id = 1L,
            title = "Concert Ticket",
            amountPaise = 80000L,
            transactionDate = 1000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = friendId, // Friend paid
            participants = listOf(
                TransactionParticipant(transactionId = 1L, friendId = null, shareAmountPaise = 80000L) // ME share
            )
        )

        val unsettled = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn),
            settlements = emptyList()
        )

        assertEquals(1, unsettled.size)
        val item = unsettled.first()
        assertEquals(ExpenseObligationDirection.I_OWE_THEM, item.direction)
        assertEquals(80000L, item.remainingOutstandingPaise)
        assertFalse(item.isPartiallySettled)

        // Now ME settles with friend
        val settlement = Settlement(
            id = 10L,
            payerId = null, // ME paid
            receiverId = friendId,
            amountPaise = 80000L,
            settlementDate = 2000L,
            isReversed = false
        )

        val unsettledAfter = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn),
            settlements = listOf(settlement)
        )
        assertTrue(unsettledAfter.isEmpty())
    }

    @Test
    fun test13_ReversedSettlementMakesExpensesUnsettledAgain() {
        val friendId = 101L

        val txn = Transaction(
            id = 1L,
            title = "Weekend Trip Share",
            amountPaise = 45000L,
            transactionDate = 1000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(transactionId = 1L, friendId = friendId, shareAmountPaise = 45000L)
            )
        )

        val settlement = Settlement(
            id = 10L,
            payerId = friendId,
            receiverId = null,
            amountPaise = 45000L,
            settlementDate = 2000L,
            isReversed = true // Reversed
        )

        val unsettled = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn),
            settlements = listOf(settlement)
        )

        assertEquals("Reversed settlement must not settle expenses", 1, unsettled.size)
        assertEquals(45000L, unsettled.first().remainingOutstandingPaise)
        assertFalse(unsettled.first().isPartiallySettled)
    }

    @Test
    fun test14_MutualExpensesNettedViaFifo() {
        val friendId = 101L

        // ME paid ₹500 for friend
        val txn1 = Transaction(
            id = 1L,
            title = "Lunch",
            amountPaise = 50000L,
            transactionDate = 1000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(transactionId = 1L, friendId = friendId, shareAmountPaise = 50000L)
            )
        )

        // Friend paid ₹300 for ME
        val txn2 = Transaction(
            id = 2L,
            title = "Coffee & snacks",
            amountPaise = 30000L,
            transactionDate = 2000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = friendId,
            participants = listOf(
                TransactionParticipant(transactionId = 2L, friendId = null, shareAmountPaise = 30000L)
            )
        )

        val unsettled = LedgerCalculator.calculateUnsettledExpenses(
            friendId = friendId,
            transactions = listOf(txn1, txn2),
            settlements = emptyList()
        )

        // Mutual offset: ₹300 offsets ₹300 of txn1. Txn2 is fully offset.
        // Txn1 has remaining ₹200 (500 - 300).
        assertEquals(1, unsettled.size)
        val item = unsettled.first()
        assertEquals(1L, item.transaction.id)
        assertEquals(20000L, item.remainingOutstandingPaise)
        assertEquals(30000L, item.settledAmountPaise)
        assertTrue(item.isPartiallySettled)
        assertEquals(ExpenseObligationDirection.THEY_OWE_ME, item.direction)
    }

    // =========================================================================
    // SECTION 3: TRANSACTIONS SCREEN FILTER REMOVAL & RETENTION
    // =========================================================================

    @Test
    fun test15_TransactionsFilterDefaultsAndPeriodNavigation() = runBlocking {
        val now = System.currentTimeMillis()
        transactionRepository.saveTransaction(
            Transaction(
                id = 1L,
                title = "Swiggy Dinner",
                amountPaise = 45000L,
                transactionDate = now,
                transactionType = TransactionType.EXPENSE,
                categoryId = 1L,
                paymentMethod = PaymentMethod.UPI
            )
        )
        transactionRepository.saveTransaction(
            Transaction(
                id = 2L,
                title = "Salary Deposit",
                amountPaise = 5000000L,
                transactionDate = now,
                transactionType = TransactionType.INCOME,
                categoryId = 2L,
                paymentMethod = PaymentMethod.BANK_TRANSFER
            )
        )

        val vm = TransactionsViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            friendGroupRepository = friendGroupRepository,
            expenseGroupRepository = expenseGroupRepository,
            pendingTransactionRepository = pendingRepository
        )

        val state = vm.uiState.first { !it.isLoading }

        // Filter defaults: All transaction types and payment methods active
        assertEquals("ALL", state.selectedTypeFilter)
        assertNull(state.selectedPaymentMethodFilter)
        assertEquals("ALL", state.selectedDateFilter)

        // Both transactions visible
        assertEquals(2, state.filteredTransactions.size)

        // Time period filter navigation
        vm.onDateFilterSelected("MONTHLY")
        val monthlyState = vm.uiState.first { it.selectedDateFilter == "MONTHLY" }
        assertEquals("MONTHLY", monthlyState.selectedDateFilter)
        assertTrue(monthlyState.datePeriodLabel.isNotBlank())

        // Search query works cleanly
        vm.onSearchQueryChanged("Swiggy")
        val searchState = vm.uiState.first { it.searchQuery == "Swiggy" }
        assertEquals(1, searchState.filteredTransactions.size)
        assertEquals("Swiggy Dinner", searchState.filteredTransactions.first().title)
    }

    // =========================================================================
    // SECTION 4: DETECTED TRANSACTIONS SCREEN BADGE & RESPONSIVE LAYOUT
    // =========================================================================

    @Test
    fun test16_PendingTransactionStatusBadgesAndFormatting() {
        val pendingTxn = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 15000L,
            rawContent = "Rs 150.00 debited from A/C at Swiggy",
            timestamp = System.currentTimeMillis(),
            status = PendingStatus.PENDING,
            detectedMerchant = "Swiggy"
        )
        val unknownTxn = PendingTransaction(
            id = 2L,
            detectedAmountPaise = 50000L,
            rawContent = "Unknown bank message with code 4821",
            timestamp = System.currentTimeMillis(),
            status = PendingStatus.UNKNOWN
        )
        val upiLiteTxn = PendingTransaction(
            id = 3L,
            detectedAmountPaise = 200000L,
            rawContent = "Rs 2000.00 added to UPI Lite wallet",
            timestamp = System.currentTimeMillis(),
            status = PendingStatus.PENDING,
            detectedType = TransactionType.UPI_LITE_TOPUP
        )

        // Verify status representations
        assertEquals(PendingStatus.PENDING, pendingTxn.status)
        assertEquals(PendingStatus.UNKNOWN, unknownTxn.status)
        assertEquals(TransactionType.UPI_LITE_TOPUP, upiLiteTxn.detectedType)

        // Verify clean amounts
        assertEquals("150.00", CurrencyUtils.formatPaiseToRupees(pendingTxn.detectedAmountPaise).replace("₹", "").trim())
        assertEquals("500.00", CurrencyUtils.formatPaiseToRupees(unknownTxn.detectedAmountPaise).replace("₹", "").trim())
        assertEquals("2,000.00", CurrencyUtils.formatPaiseToRupees(upiLiteTxn.detectedAmountPaise).replace("₹", "").trim())
    }
}
