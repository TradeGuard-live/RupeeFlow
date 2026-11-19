package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.ui.screens.overview.OverviewViewModel
import com.naman.rupeeflow.ui.screens.transactions.TransactionsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ManualExpenseCategoryLearningAndFriendHistoryQATest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var pendingRepository: PendingTransactionRepository
    private lateinit var merchantPreferenceRepository: MerchantPreferenceRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        merchantPreferenceRepository = MerchantPreferenceRepositoryImpl(database.inputUsageDao())
        categoryRepository = CategoryRepositoryImpl(database.categoryDao())
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
        pendingRepository = PendingTransactionRepositoryImpl(
            pendingDao = database.pendingTransactionDao(),
            transactionDao = database.transactionDao()
        )

        runBlocking {
            database.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "Food & Dining", colorHex = "#FF5722", iconName = "restaurant"))
            database.categoryDao().insertCategory(CategoryEntity(id = 2L, name = "Groceries", colorHex = "#4CAF50", iconName = "shopping_cart"))
            database.categoryDao().insertCategory(CategoryEntity(id = 3L, name = "Shopping", colorHex = "#9C27B0", iconName = "store"))
            database.categoryDao().insertCategory(CategoryEntity(id = 4L, name = "Transportation", colorHex = "#2196F3", iconName = "directions_car"))
            database.categoryDao().insertCategory(CategoryEntity(id = 5L, name = "Entertainment", colorHex = "#E91E63", iconName = "movie"))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // =========================================================================
    // PART 1: MANUAL EXPENSE STORE NAME -> AUTOMATIC CATEGORY SELECTION
    // =========================================================================

    @Test
    fun testManualExpenseStoreName_LearnsCategoryOnSave() = runBlocking {
        val overviewVm = OverviewViewModel(
            transactionRepository = transactionRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            pendingTransactionRepository = pendingRepository,
            categoryRepository = categoryRepository,
            merchantPreferenceRepository = merchantPreferenceRepository
        )

        // Save a manual expense with store name "Starbucks" and Category 1 ("Food & Dining")
        val txn = Transaction(
            title = "Morning Coffee",
            amountPaise = 35000L,
            categoryId = 1L,
            merchantName = "Starbucks",
            transactionType = TransactionType.EXPENSE,
            transactionDate = System.currentTimeMillis()
        )
        transactionRepository.saveTransaction(txn)

        // Verify learned preference in repository
        val suggestedCatId = merchantPreferenceRepository.getSuggestedCategory("Starbucks")
        assertEquals(1L, suggestedCatId)

        // Verify ViewModel auto-suggests category for the same store name
        val suggestedCategory = overviewVm.getSuggestedCategoryForMerchant("Starbucks")
        assertNotNull(suggestedCategory)
        assertEquals(1L, suggestedCategory?.id)
        assertEquals("Food & Dining", suggestedCategory?.name)
    }

    @Test
    fun testManualExpenseStoreName_AutoSelectsCategoryForSameStoreName_TransactionsViewModel() = runBlocking {
        val transactionsVm = TransactionsViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            merchantPreferenceRepository = merchantPreferenceRepository
        )

        // Save expense with store name "Domino's" and Category 1
        val txn = Transaction(
            title = "Pizza Dinner",
            amountPaise = 55000L,
            categoryId = 1L,
            merchantName = "Domino's",
            transactionType = TransactionType.EXPENSE,
            transactionDate = System.currentTimeMillis()
        )
        transactionRepository.saveTransaction(txn)

        // When entering the same store name later (exact or case variant), category is automatically selected
        val matchExact = transactionsVm.getSuggestedCategoryForMerchant("Domino's")
        assertNotNull(matchExact)
        assertEquals(1L, matchExact?.id)

        val matchLower = transactionsVm.getSuggestedCategoryForMerchant("dominos")
        assertNotNull(matchLower)
        assertEquals(1L, matchLower?.id)
    }

    @Test
    fun testManualExpenseStoreName_CategoryLearnedWhenEnteredInTitle() = runBlocking {
        val overviewVm = OverviewViewModel(
            transactionRepository = transactionRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            pendingTransactionRepository = pendingRepository,
            categoryRepository = categoryRepository,
            merchantPreferenceRepository = merchantPreferenceRepository
        )

        // User enters "Decathlon" as title without filling optional merchant field
        val txn = Transaction(
            title = "Decathlon",
            amountPaise = 199900L,
            categoryId = 3L, // Shopping
            merchantName = null,
            transactionType = TransactionType.EXPENSE,
            transactionDate = System.currentTimeMillis()
        )
        transactionRepository.saveTransaction(txn)

        // Later user types "Decathlon" into store name or title
        val suggested = overviewVm.getSuggestedCategoryForMerchant("Decathlon")
        assertNotNull(suggested)
        assertEquals(3L, suggested?.id)
        assertEquals("Shopping", suggested?.name)
    }

    @Test
    fun testManualExpenseStoreName_CategoryCorrectionUpdatesLearnedPreference() = runBlocking {
        val overviewVm = OverviewViewModel(
            transactionRepository = transactionRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            pendingTransactionRepository = pendingRepository,
            categoryRepository = categoryRepository,
            merchantPreferenceRepository = merchantPreferenceRepository
        )

        // First transaction with "Zepto" saved as Shopping (3)
        val txn1 = Transaction(
            title = "Zepto quick delivery",
            amountPaise = 25000L,
            categoryId = 3L,
            merchantName = "Zepto",
            transactionType = TransactionType.EXPENSE,
            transactionDate = System.currentTimeMillis()
        )
        transactionRepository.saveTransaction(txn1)

        // Later user saves "Zepto" with Groceries (2)
        val txn2 = Transaction(
            title = "Zepto groceries",
            amountPaise = 42000L,
            categoryId = 2L,
            merchantName = "Zepto",
            transactionType = TransactionType.EXPENSE,
            transactionDate = System.currentTimeMillis() + 1000L
        )
        transactionRepository.saveTransaction(txn2)

        // Suggestion must reflect the latest preferred category (Groceries)
        val suggested = overviewVm.getSuggestedCategoryForMerchant("Zepto")
        assertNotNull(suggested)
        assertEquals(2L, suggested?.id)
        assertEquals("Groceries", suggested?.name)
    }

    @Test
    fun testManualExpenseStoreName_IndependentStoresDoNotInterfere() = runBlocking {
        val overviewVm = OverviewViewModel(
            transactionRepository = transactionRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            pendingTransactionRepository = pendingRepository,
            categoryRepository = categoryRepository,
            merchantPreferenceRepository = merchantPreferenceRepository
        )

        transactionRepository.saveTransaction(Transaction(title = "Uber ride", amountPaise = 30000L, categoryId = 4L, merchantName = "Uber"))
        transactionRepository.saveTransaction(Transaction(title = "Swiggy food", amountPaise = 45000L, categoryId = 1L, merchantName = "Swiggy"))
        transactionRepository.saveTransaction(Transaction(title = "PVR Cinema", amountPaise = 80000L, categoryId = 5L, merchantName = "PVR Cinemas"))

        assertEquals(4L, overviewVm.getSuggestedCategoryForMerchant("Uber")?.id)
        assertEquals(1L, overviewVm.getSuggestedCategoryForMerchant("Swiggy")?.id)
        assertEquals(5L, overviewVm.getSuggestedCategoryForMerchant("PVR Cinemas")?.id)
    }

    // =========================================================================
    // PART 2: SPLIT PRO FRIEND EXPENSE HISTORY & STATUS DISPLAY
    // =========================================================================

    @Test
    fun testFriendExpenseHistory_Scenario1_SingleUnpaidExpense() {
        val friendId = 10L
        val txns = listOf(
            Transaction(
                id = 101L,
                title = "Dinner",
                amountPaise = 10000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null, // Me paid
                participants = listOf(
                    TransactionParticipant(friendId = friendId, friendName = "Friend", shareAmountPaise = 5000L),
                    TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 5000L)
                )
            )
        )
        val settlements = emptyList<Settlement>()

        val history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, settlements)
        val outstanding = LedgerCalculator.calculateUnsettledExpenses(friendId, txns, settlements)

        assertEquals(1, history.size)
        assertEquals(1, outstanding.size)

        val item = history.first()
        assertTrue(item.isUnpaid)
        assertFalse(item.isPartiallySettled)
        assertFalse(item.isFullySettled)
        assertEquals(5000L, item.originalShareAmountPaise)
        assertEquals(0L, item.settledAmountPaise)
        assertEquals(5000L, item.remainingOutstandingPaise)
    }

    @Test
    fun testFriendExpenseHistory_Scenario2_SinglePartiallySettledExpense() {
        val friendId = 10L
        val txns = listOf(
            Transaction(
                id = 102L,
                title = "Groceries",
                amountPaise = 20000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null, // Me paid
                participants = listOf(
                    TransactionParticipant(friendId = friendId, friendName = "Friend", shareAmountPaise = 10000L),
                    TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 10000L)
                )
            )
        )
        // Friend paid 4000 paise (₹40) towards the ₹100 share
        val settlements = listOf(
            Settlement(id = 1L, payerId = friendId, receiverId = null, amountPaise = 4000L, settlementDate = 2000L)
        )

        val history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, settlements)
        val outstanding = LedgerCalculator.calculateUnsettledExpenses(friendId, txns, settlements)

        assertEquals(1, history.size)
        assertEquals(1, outstanding.size)

        val item = history.first()
        assertFalse(item.isUnpaid)
        assertTrue(item.isPartiallySettled)
        assertFalse(item.isFullySettled)
        assertEquals(10000L, item.originalShareAmountPaise)
        assertEquals(4000L, item.settledAmountPaise)
        assertEquals(6000L, item.remainingOutstandingPaise)
    }

    @Test
    fun testFriendExpenseHistory_Scenario3_SingleFullySettledExpense() {
        val friendId = 10L
        val txns = listOf(
            Transaction(
                id = 103L,
                title = "Movie Tickets",
                amountPaise = 10000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null, // Me paid
                participants = listOf(
                    TransactionParticipant(friendId = friendId, friendName = "Friend", shareAmountPaise = 5000L),
                    TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 5000L)
                )
            )
        )
        // Friend settled full ₹50 share
        val settlements = listOf(
            Settlement(id = 1L, payerId = friendId, receiverId = null, amountPaise = 5000L, settlementDate = 2000L)
        )

        val history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, settlements)
        val outstanding = LedgerCalculator.calculateUnsettledExpenses(friendId, txns, settlements)

        // Outstanding tab must be empty because expense is fully settled
        assertEquals(0, outstanding.size)

        // Complete history MUST retain the fully settled expense
        assertEquals(1, history.size)
        val item = history.first()
        assertTrue(item.isFullySettled)
        assertFalse(item.isPartiallySettled)
        assertFalse(item.isUnpaid)
        assertEquals(5000L, item.originalShareAmountPaise)
        assertEquals(5000L, item.settledAmountPaise)
        assertEquals(0L, item.remainingOutstandingPaise)
    }

    @Test
    fun testFriendExpenseHistory_Scenario4_MultipleExpensesMixedStatus() {
        val friendId = 10L
        val txns = listOf(
            Transaction(
                id = 1L,
                title = "Oldest Dinner",
                amountPaise = 10000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 5000L))
            ),
            Transaction(
                id = 2L,
                title = "Middle Lunch",
                amountPaise = 20000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 2000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 10000L))
            ),
            Transaction(
                id = 3L,
                title = "Newest Snacks",
                amountPaise = 40000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 3000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 20000L))
            )
        )
        // Settlement of ₹100 (10000 paise): FIFO fully settles Txn 1 (₹50) and partially settles Txn 2 (₹50 of ₹100)
        val settlements = listOf(
            Settlement(id = 1L, payerId = friendId, receiverId = null, amountPaise = 10000L, settlementDate = 4000L)
        )

        val history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, settlements)
        val outstanding = LedgerCalculator.calculateUnsettledExpenses(friendId, txns, settlements)

        // Outstanding shows Txn 2 (partially settled) and Txn 3 (unpaid)
        assertEquals(2, outstanding.size)
        assertTrue(outstanding.any { it.transaction.id == 2L && it.isPartiallySettled })
        assertTrue(outstanding.any { it.transaction.id == 3L && it.isUnpaid })

        // Complete history shows all 3 transactions
        assertEquals(3, history.size)
        val item1 = history.first { it.transaction.id == 1L }
        assertTrue(item1.isFullySettled)
        assertEquals(0L, item1.remainingOutstandingPaise)
        assertEquals(5000L, item1.settledAmountPaise)

        val item2 = history.first { it.transaction.id == 2L }
        assertTrue(item2.isPartiallySettled)
        assertEquals(5000L, item2.remainingOutstandingPaise)
        assertEquals(5000L, item2.settledAmountPaise)

        val item3 = history.first { it.transaction.id == 3L }
        assertTrue(item3.isUnpaid)
        assertEquals(20000L, item3.remainingOutstandingPaise)
        assertEquals(0L, item3.settledAmountPaise)
    }

    @Test
    fun testFriendExpenseHistory_Scenario5_MultiplePartialSettlementsSingleExpense() {
        val friendId = 10L
        val txns = listOf(
            Transaction(
                id = 201L,
                title = "Weekend Trip",
                amountPaise = 20000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 10000L))
            )
        )

        // First partial settlement of ₹30
        val settlements1 = listOf(
            Settlement(id = 1L, payerId = friendId, receiverId = null, amountPaise = 3000L, settlementDate = 2000L)
        )
        var history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, settlements1)
        assertEquals(1, history.size)
        assertTrue(history.first().isPartiallySettled)
        assertEquals(3000L, history.first().settledAmountPaise)
        assertEquals(7000L, history.first().remainingOutstandingPaise)

        // Second partial settlement of ₹30
        val settlements2 = settlements1 + Settlement(id = 2L, payerId = friendId, receiverId = null, amountPaise = 3000L, settlementDate = 3000L)
        history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, settlements2)
        assertTrue(history.first().isPartiallySettled)
        assertEquals(6000L, history.first().settledAmountPaise)
        assertEquals(4000L, history.first().remainingOutstandingPaise)

        // Third settlement of ₹40 completing the ₹100
        val settlements3 = settlements2 + Settlement(id = 3L, payerId = friendId, receiverId = null, amountPaise = 4000L, settlementDate = 4000L)
        history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, settlements3)
        assertTrue(history.first().isFullySettled)
        assertEquals(10000L, history.first().settledAmountPaise)
        assertEquals(0L, history.first().remainingOutstandingPaise)
    }

    @Test
    fun testFriendExpenseHistory_Scenario6_SettlementReversalRecalculatesStatus() {
        val friendId = 10L
        val txns = listOf(
            Transaction(
                id = 301L,
                title = "Concert Ticket",
                amountPaise = 15000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 15000L))
            )
        )

        // Active settlement of ₹150
        val settlement = Settlement(id = 1L, payerId = friendId, receiverId = null, amountPaise = 15000L, settlementDate = 2000L, isReversed = false)
        var history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, listOf(settlement))
        assertTrue(history.first().isFullySettled)
        assertEquals(0L, history.first().remainingOutstandingPaise)

        // Reverse the settlement
        val reversedSettlement = settlement.copy(isReversed = true)
        history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, listOf(reversedSettlement))
        val outstanding = LedgerCalculator.calculateUnsettledExpenses(friendId, txns, listOf(reversedSettlement))

        assertEquals(1, outstanding.size)
        assertTrue(history.first().isUnpaid)
        assertEquals(15000L, history.first().remainingOutstandingPaise)
        assertEquals(0L, history.first().settledAmountPaise)
    }

    @Test
    fun testFriendExpenseHistory_Scenario7_AddNewExpenseAfterFullSettlement() {
        val friendId = 10L
        val txn1 = Transaction(
            id = 401L,
            title = "First Expense",
            amountPaise = 5000L,
            transactionType = TransactionType.EXPENSE,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 5000L))
        )
        val settlement1 = Settlement(id = 1L, payerId = friendId, receiverId = null, amountPaise = 5000L, settlementDate = 2000L)

        // Add a new expense after earlier is fully settled
        val txn2 = Transaction(
            id = 402L,
            title = "Second Expense",
            amountPaise = 8000L,
            transactionType = TransactionType.EXPENSE,
            transactionDate = 3000L,
            paidByFriendId = null,
            participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 8000L))
        )

        val allTxns = listOf(txn1, txn2)
        val allSettlements = listOf(settlement1)

        val history = LedgerCalculator.calculateFriendExpenseHistory(friendId, allTxns, allSettlements)
        val outstanding = LedgerCalculator.calculateUnsettledExpenses(friendId, allTxns, allSettlements)

        // Outstanding tab shows only Txn 2
        assertEquals(1, outstanding.size)
        assertEquals(402L, outstanding.first().transaction.id)
        assertEquals(8000L, outstanding.first().remainingOutstandingPaise)

        // Complete history shows both Txn 1 (Fully settled) and Txn 2 (Unpaid)
        assertEquals(2, history.size)
        val hist1 = history.first { it.transaction.id == 401L }
        val hist2 = history.first { it.transaction.id == 402L }

        assertTrue(hist1.isFullySettled)
        assertEquals(0L, hist1.remainingOutstandingPaise)

        assertTrue(hist2.isUnpaid)
        assertEquals(8000L, hist2.remainingOutstandingPaise)
    }

    @Test
    fun testFriendExpenseHistory_Scenario8_AllActivityNoDuplicatesChronological() {
        val friendId = 10L
        val txns = listOf(
            Transaction(
                id = 501L,
                title = "Coffee",
                amountPaise = 4000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 1000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 4000L))
            ),
            Transaction(
                id = 502L,
                title = "Lunch",
                amountPaise = 12000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = 3000L,
                paidByFriendId = null,
                participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 12000L))
            )
        )
        val settlements = listOf(
            Settlement(id = 1L, payerId = friendId, receiverId = null, amountPaise = 4000L, settlementDate = 2000L)
        )

        val history = LedgerCalculator.calculateFriendExpenseHistory(friendId, txns, settlements)
        assertEquals(2, history.size)

        // In All Activity, we combine history expenses and settlements
        val allActivity = (history.map { it.transaction.transactionDate to "EXPENSE_${it.transaction.id}" } +
                settlements.map { it.settlementDate to "SETTLEMENT_${it.id}" })
            .sortedByDescending { it.first }

        assertEquals(3, allActivity.size)
        // Verify chronological descending order: 3000L (Lunch), 2000L (Settlement 1), 1000L (Coffee)
        assertEquals("EXPENSE_502", allActivity[0].second)
        assertEquals("SETTLEMENT_1", allActivity[1].second)
        assertEquals("EXPENSE_501", allActivity[2].second)
    }

    @Test
    fun testFriendExpenseHistory_Scenario9_EditingExpenseAfterPartialSettlement() {
        val friendId = 10L
        val originalTxn = Transaction(
            id = 601L,
            title = "Team Dinner",
            amountPaise = 20000L,
            transactionType = TransactionType.EXPENSE,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 10000L))
        )
        // Settlement of ₹40 (4000 paise)
        val settlement = Settlement(id = 1L, payerId = friendId, receiverId = null, amountPaise = 4000L, settlementDate = 2000L)

        // Initial state before edit: ₹100 share, ₹40 settled, ₹60 remaining
        var history = LedgerCalculator.calculateFriendExpenseHistory(friendId, listOf(originalTxn), listOf(settlement))
        assertEquals(1, history.size)
        assertTrue(history.first().isPartiallySettled)
        assertEquals(10000L, history.first().originalShareAmountPaise)
        assertEquals(4000L, history.first().settledAmountPaise)
        assertEquals(6000L, history.first().remainingOutstandingPaise)

        // Case A: User edits expense to increase friend's share to ₹150 (15000 paise)
        val editedTxnIncreased = originalTxn.copy(
            amountPaise = 30000L,
            participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 15000L))
        )
        history = LedgerCalculator.calculateFriendExpenseHistory(friendId, listOf(editedTxnIncreased), listOf(settlement))
        assertEquals(1, history.size)
        assertTrue(history.first().isPartiallySettled)
        assertEquals(15000L, history.first().originalShareAmountPaise)
        assertEquals(4000L, history.first().settledAmountPaise)
        assertEquals(11000L, history.first().remainingOutstandingPaise)

        // Case B: User edits expense to decrease friend's share to ₹40 (matching settled amount)
        val editedTxnMatching = originalTxn.copy(
            amountPaise = 8000L,
            participants = listOf(TransactionParticipant(friendId = friendId, shareAmountPaise = 4000L))
        )
        history = LedgerCalculator.calculateFriendExpenseHistory(friendId, listOf(editedTxnMatching), listOf(settlement))
        assertEquals(1, history.size)
        assertTrue(history.first().isFullySettled)
        assertEquals(4000L, history.first().originalShareAmountPaise)
        assertEquals(4000L, history.first().settledAmountPaise)
        assertEquals(0L, history.first().remainingOutstandingPaise)
    }
}

