package com.naman.rupeeflow.qa

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.data.repository.TransactionRepositoryImpl
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DateTimeUtils
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Calendar

/**
 * RupeeFlow Production QA Test Plan
 * Section 3: Transaction creation, editing and deletion — TC021 to TC035
 */
@RunWith(RobolectricTestRunner::class)
class TransactionLifecycleQATest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository

    private val friendA = Friend(id = 101L, name = "Friend A")
    private val friendB = Friend(id = 102L, name = "Friend B")
    private val friendC = Friend(id = 103L, name = "Friend C")

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao()
        )

        runBlocking {
            database.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF0000", isDefault = true))
            database.categoryDao().insertCategory(CategoryEntity(id = 2L, name = "Transportation", iconName = "directions_car", colorHex = "#00FF00", isDefault = true))
            database.friendDao().insertFriend(FriendEntity(id = 101L, name = "Friend A", createdAt = 1000L))
            database.friendDao().insertFriend(FriendEntity(id = 102L, name = "Friend B", createdAt = 1000L))
            database.friendDao().insertFriend(FriendEntity(id = 103L, name = "Friend C", createdAt = 1000L))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test_TC021_CreateNormalExpense() = runBlocking {
        // Steps: Create a ₹500 food expense paid by Me.
        val expense = Transaction(
            title = "Lunch",
            amountPaise = 50000L,
            categoryId = 1L,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null
        )
        val id = transactionRepository.saveTransaction(expense)
        assertTrue(id > 0L)

        // Expected: Exactly one transaction is stored and dashboard spending increases correctly.
        val retrieved = transactionRepository.getTransactionById(id)
        assertNotNull(retrieved)
        assertEquals(50000L, retrieved!!.amountPaise)
        assertEquals(TransactionType.EXPENSE, retrieved.transactionType)

        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = listOf(retrieved),
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = 0L,
            now = System.currentTimeMillis() + 10000L
        )
        assertEquals(50000L, summary.totalExpenseThisMonthPaise)
    }

    @Test
    fun test_TC022_CreateIncomeTransaction() = runBlocking {
        // Steps: Record ₹5,000 income.
        val income = Transaction(
            title = "Freelance Consulting",
            amountPaise = 500000L,
            categoryId = 1L,
            transactionType = TransactionType.INCOME,
            paidByFriendId = null
        )
        val id = transactionRepository.saveTransaction(income)
        val retrieved = transactionRepository.getTransactionById(id)!!

        // Expected: Income increases by ₹5,000. Not added to shared friend receivables.
        assertEquals(500000L, retrieved.amountPaise)
        assertEquals(TransactionType.INCOME, retrieved.transactionType)

        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = listOf(retrieved),
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = 0L,
            now = System.currentTimeMillis() + 10000L
        )
        assertEquals(500000L, summary.totalIncomeThisMonthPaise)
        assertEquals(0L, summary.totalExpenseThisMonthPaise)
        assertEquals(0L, summary.totalOwedToMePaise)
    }

    @Test
    fun test_TC023_CreateRefund() = runBlocking {
        // Steps: Record a ₹200 refund using the supported refund workflow.
        val refund = Transaction(
            title = "E-Commerce Return",
            amountPaise = 20000L,
            categoryId = 1L,
            transactionType = TransactionType.REFUND
        )
        val id = transactionRepository.saveTransaction(refund)
        val retrieved = transactionRepository.getTransactionById(id)!!

        // Expected: Refund is classified separately from ordinary income and affects net spending.
        assertEquals(TransactionType.REFUND, retrieved.transactionType)

        val expense = Transaction(title = "Item", amountPaise = 100000L, categoryId = 1L, transactionType = TransactionType.EXPENSE)
        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = listOf(expense, retrieved),
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = 0L,
            now = System.currentTimeMillis() + 10000L
        )
        assertEquals(100000L, summary.totalExpenseThisMonthPaise)
        assertEquals(20000L, summary.totalRefundThisMonthPaise)
        assertEquals(80000L, summary.netSpendingPaise) // ₹1000 - ₹200 = ₹800
    }

    @Test
    fun test_TC024_CreateTransfer() = runBlocking {
        // Steps: Record a ₹1,000 transfer between supported accounts.
        val transfer = Transaction(
            title = "Bank to Wallet",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionType = TransactionType.TRANSFER
        )
        val id = transactionRepository.saveTransaction(transfer)
        val retrieved = transactionRepository.getTransactionById(id)!!

        // Expected: Transfer is not counted as ordinary income or expense.
        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = listOf(retrieved),
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = 0L,
            now = System.currentTimeMillis() + 10000L
        )
        assertEquals(0L, summary.totalExpenseThisMonthPaise)
        assertEquals(0L, summary.totalIncomeThisMonthPaise)
        assertEquals(0L, summary.netSpendingPaise)
    }

    @Test
    fun test_TC025_CreateTransactionPaidByFriend() = runBlocking {
        // Steps: Friend A pays ₹1,000 for an expense shared between A and Me, with Me's share ₹400.
        val txn = Transaction(
            title = "Dinner Paid by A",
            amountPaise = 100000L,
            paidByFriendId = 101L, // Friend A paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 40000L), // Me's share ₹400
                TransactionParticipant(friendId = 101L, shareAmountPaise = 60000L) // A's share ₹600
            )
        )
        val id = transactionRepository.saveTransaction(txn)
        val retrieved = transactionRepository.getTransactionById(id)!!

        // Expected: Me owes A ₹400. A does not owe Me the full expense.
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(retrieved), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledgerA.status)
        assertEquals(40000L, ledgerA.totalIOweThemPaise)
        assertEquals(0L, ledgerA.totalTheyOweMePaise)
        assertEquals(-40000L, ledgerA.netBalancePaise)
    }

    @Test
    fun test_TC026_EditTransactionTitle() = runBlocking {
        // Steps: Change an existing transaction title.
        val original = Transaction(title = "Original Title", amountPaise = 50000L, categoryId = 1L)
        val id = transactionRepository.saveTransaction(original)
        val retrievedBefore = transactionRepository.getTransactionById(id)!!

        val edited = retrievedBefore.copy(title = "Updated New Title")
        val updatedId = transactionRepository.saveTransaction(edited)

        // Expected: Same transaction ID remains. No duplicate transaction created.
        assertEquals(id, updatedId)
        val retrievedAfter = transactionRepository.getTransactionById(id)!!
        assertEquals("Updated New Title", retrievedAfter.title)
        assertEquals(retrievedBefore.createdAt, retrievedAfter.createdAt)
    }

    @Test
    fun test_TC027_EditTransactionAmount() = runBlocking {
        // Steps: Change ₹1,000 to ₹1,200.
        val original = Transaction(
            title = "Dinner",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        val id = transactionRepository.saveTransaction(original)

        // Edit amount to ₹1,200 (₹600 each)
        val edited = transactionRepository.getTransactionById(id)!!.copy(
            amountPaise = 120000L,
            participants = listOf(
                TransactionParticipant(transactionId = id, friendId = null, shareAmountPaise = 60000L),
                TransactionParticipant(transactionId = id, friendId = 101L, shareAmountPaise = 60000L)
            )
        )
        transactionRepository.saveTransaction(edited)

        // Expected: All dependent calculations use ₹1,200. Old amount is not counted.
        val retrieved = transactionRepository.getTransactionById(id)!!
        assertEquals(120000L, retrieved.amountPaise)
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(retrieved), emptyList())
        assertEquals(60000L, ledgers.first().totalTheyOweMePaise)
    }

    @Test
    fun test_TC028_EditTransactionDate() = runBlocking {
        val now = System.currentTimeMillis()
        val startOfThisMonth = DateTimeUtils.getStartOfMonth(now)
        val prevMonthDate = startOfThisMonth - 86400000L * 5 // 5 days prior to month start

        // Steps: Move a transaction from the current month to the previous month.
        val original = Transaction(title = "Monthly Utility", amountPaise = 50000L, categoryId = 1L, transactionDate = now)
        val id = transactionRepository.saveTransaction(original)

        val edited = transactionRepository.getTransactionById(id)!!.copy(transactionDate = prevMonthDate)
        transactionRepository.saveTransaction(edited)

        // Expected: Monthly analytics and date filters update correctly (excluded from this month).
        val retrieved = transactionRepository.getTransactionById(id)!!
        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = listOf(retrieved),
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = startOfThisMonth,
            now = now
        )
        assertEquals(0L, summary.totalExpenseThisMonthPaise)
    }

    @Test
    fun test_TC029_EditTransactionCategory() = runBlocking {
        // Steps: Change Food (1) to Transportation (2).
        val original = Transaction(title = "Metro Card", amountPaise = 100000L, categoryId = 1L)
        val id = transactionRepository.saveTransaction(original)

        val edited = transactionRepository.getTransactionById(id)!!.copy(categoryId = 2L)
        transactionRepository.saveTransaction(edited)

        // Expected: Category analytics update. Financial amount remains unchanged.
        val retrieved = transactionRepository.getTransactionById(id)!!
        assertEquals(2L, retrieved.categoryId)
        assertEquals(100000L, retrieved.amountPaise)
    }

    @Test
    fun test_TC030_EditPaymentMethod() = runBlocking {
        // Steps: Change UPI to Cash.
        val original = Transaction(title = "Groceries", amountPaise = 40000L, categoryId = 1L, paymentMethod = PaymentMethod.UPI)
        val id = transactionRepository.saveTransaction(original)

        val edited = transactionRepository.getTransactionById(id)!!.copy(paymentMethod = PaymentMethod.CASH)
        transactionRepository.saveTransaction(edited)

        // Expected: Payment-method analytics update without changing amount or friend ledger.
        val retrieved = transactionRepository.getTransactionById(id)!!
        assertEquals(PaymentMethod.CASH, retrieved.paymentMethod)
        assertEquals(40000L, retrieved.amountPaise)
    }

    @Test
    fun test_TC031_EditPayer() = runBlocking {
        // Steps: Change payer from Me to Friend A.
        val original = Transaction(
            title = "Dinner",
            amountPaise = 100000L,
            paidByFriendId = null, // Me paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        val id = transactionRepository.saveTransaction(original)

        // Change payer to Friend A
        val edited = transactionRepository.getTransactionById(id)!!.copy(
            paidByFriendId = 101L
        )
        transactionRepository.saveTransaction(edited)

        // Expected: Ledger direction is recalculated correctly. Me now owes A ₹500.
        val retrieved = transactionRepository.getTransactionById(id)!!
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(retrieved), emptyList())
        val ledgerA = ledgers.first { it.id == 101L }
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ledgerA.status)
        assertEquals(50000L, ledgerA.totalIOweThemPaise)
    }

    @Test
    fun test_TC032_EditParticipantList() = runBlocking {
        // Steps: Remove Friend B and add Friend C.
        val original = Transaction(
            title = "Shared Cab",
            amountPaise = 60000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 30000L) // Friend B
            )
        )
        val id = transactionRepository.saveTransaction(original)

        // Replace Friend B with Friend C
        val edited = transactionRepository.getTransactionById(id)!!.copy(
            participants = listOf(
                TransactionParticipant(transactionId = id, friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(transactionId = id, friendId = 103L, shareAmountPaise = 30000L) // Friend C
            )
        )
        transactionRepository.saveTransaction(edited)

        // Expected: Friend B's old share is removed and Friend C's new share is included exactly once.
        val retrieved = transactionRepository.getTransactionById(id)!!
        assertEquals(2, retrieved.participants.size)
        assertTrue(retrieved.participants.none { it.friendId == 102L })
        assertTrue(retrieved.participants.any { it.friendId == 103L })

        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendB, friendC), listOf(retrieved), emptyList())
        assertEquals(0L, ledgers.first { it.id == 102L }.totalTheyOweMePaise)
        assertEquals(30000L, ledgers.first { it.id == 103L }.totalTheyOweMePaise)
    }

    @Test
    fun test_TC033_EditAnIgnoredTransaction() = runBlocking {
        // Steps: Ignore a transaction and then edit it.
        val original = Transaction(
            title = "Ignored Expense",
            amountPaise = 50000L,
            categoryId = 1L,
            transactionStatus = TransactionStatus.IGNORED
        )
        val id = transactionRepository.saveTransaction(original)

        val edited = transactionRepository.getTransactionById(id)!!.copy(title = "Edited Ignored Expense")
        transactionRepository.saveTransaction(edited)

        // Expected: Its ignored status is preserved. It remains excluded from active totals.
        val retrieved = transactionRepository.getTransactionById(id)!!
        assertEquals(TransactionStatus.IGNORED, retrieved.transactionStatus)

        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = listOf(retrieved),
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = 0L,
            now = System.currentTimeMillis() + 10000L
        )
        assertEquals(0L, summary.totalExpenseThisMonthPaise)
    }

    @Test
    fun test_TC034_DeleteTransaction() = runBlocking {
        // Steps: Delete a transaction that affects a friend's balance.
        val txn = Transaction(
            title = "Lunch",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L)
            )
        )
        val id = transactionRepository.saveTransaction(txn)
        assertNotNull(transactionRepository.getTransactionById(id))

        transactionRepository.deleteTransaction(id)

        // Expected: Transaction and participant records are deleted consistently. Ledger recalculates.
        assertNull(transactionRepository.getTransactionById(id))
        val allTxns = transactionRepository.getAllTransactionsList()
        val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friendA), allTxns, emptyList())
        assertEquals(0L, ledgers.first().totalTheyOweMePaise)
        assertEquals(FriendBalanceStatus.SETTLED, ledgers.first().status)
    }

    @Test
    fun test_TC035_PreventAccidentalDuplicateSubmission() = runBlocking {
        // Steps: Tap Save repeatedly or trigger rapid simultaneous saves for the same transaction.
        val txn = Transaction(title = "Coffee", amountPaise = 25000L, categoryId = 1L)
        val id1 = transactionRepository.saveTransaction(txn)

        // If the same entity with ID is saved again, it updates the existing entity rather than inserting a duplicate
        val existing = transactionRepository.getTransactionById(id1)!!
        val id2 = transactionRepository.saveTransaction(existing)
        assertEquals(id1, id2)

        val count = transactionRepository.getAllTransactionsList().size
        assertEquals(1, count)
    }
}
