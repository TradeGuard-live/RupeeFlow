package com.naman.rupeeflow.ui.screens.transactions

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.ExpenseGroupEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.model.*
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

@RunWith(RobolectricTestRunner::class)
class ExpenseGroupedTransactionsTest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var expenseGroupRepository: ExpenseGroupRepository

    private val fixedTimestamp = 1731456000000L // 13 Nov 2026

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = RuntimeEnvironment.getApplication()
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
        expenseGroupRepository = ExpenseGroupRepositoryImpl(
            expenseGroupDao = database.expenseGroupDao(),
            transactionDao = database.transactionDao()
        )

        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#EF4444")
            )
            database.categoryDao().insertCategory(
                CategoryEntity(id = 2L, name = "Travel", iconName = "flight", colorHex = "#3B82F6")
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): TransactionsViewModel {
        return TransactionsViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            friendGroupRepository = null,
            expenseGroupRepository = expenseGroupRepository
        )
    }

    // Test 36: Create group with 1 txn
    @Test
    fun testCreateGroupWithOneTransaction() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Farm & Caves", description = "Weekend trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(
                id = 101L,
                title = "Tent Stay",
                amountPaise = 300000L,
                categoryId = 2L,
                transactionDate = fixedTimestamp,
                expenseGroupId = 1L,
                expenseGroupName = "Farm & Caves"
            )
        )

        val vm = createViewModel()
        val state = vm.uiState.first { it.transactionClusters.isNotEmpty() }

        assertEquals(1, state.transactionClusters.size)
        val cluster = state.transactionClusters[0]
        assertEquals("Farm & Caves", cluster.name)
        assertEquals(1, cluster.transactionCount)
        assertEquals(300000L, cluster.totalPersonalExpensePaise)
    }

    // Test 37: Multiple txns in group correctly aggregated
    @Test
    fun testMultipleTransactionsInGroup() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Goa Trip")
        database.expenseGroupDao().insertExpenseGroup(group)
        database.friendDao().insertFriend(com.naman.rupeeflow.data.local.entity.FriendEntity(id = 10L, name = "Friend"))

        transactionRepository.saveTransaction(
            Transaction(
                id = 1L,
                title = "Hotel",
                amountPaise = 500000L,
                transactionDate = fixedTimestamp,
                expenseGroupId = 1L,
                expenseGroupName = "Goa Trip"
            )
        )
        // Split transaction: gross 200000, user's share 100000
        transactionRepository.saveTransaction(
            Transaction(
                id = 2L,
                title = "Shack Dinner",
                amountPaise = 200000L,
                transactionDate = fixedTimestamp + 1000,
                expenseGroupId = 1L,
                expenseGroupName = "Goa Trip",
                participants = listOf(
                    TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 100000L),
                    TransactionParticipant(friendId = 10L, friendName = "Friend", shareAmountPaise = 100000L)
                )
            )
        )

        val vm = createViewModel()
        val state = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        val cluster = state.transactionClusters.first { it.name == "Goa Trip" }

        assertEquals(2, cluster.transactionCount)
        // Hotel 500000 + personal share 100000 = 600000
        assertEquals(600000L, cluster.totalPersonalExpensePaise)
    }

    // Test 38: Collapsed card state
    @Test
    fun testCollapsedCardStateInitially() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Cab", amountPaise = 50000L, expenseGroupId = 1L)
        )

        val vm = createViewModel()
        val state = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        val cluster = state.transactionClusters[0]
        assertFalse("Cluster must be collapsed initially when not in expandedKeys", cluster.isExpanded)
    }

    // Test 39: Expand/collapse toggle
    @Test
    fun testToggleGroupExpansion() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Cab", amountPaise = 50000L, expenseGroupId = 1L)
        )

        val vm = createViewModel()
        val initial = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        val key = initial.transactionClusters[0].key

        vm.toggleGroupExpansion(key)
        val expandedState = vm.uiState.first { it.expandedGroupKeys.contains(key) }
        assertTrue(expandedState.transactionClusters[0].isExpanded)

        vm.toggleGroupExpansion(key)
        val collapsedState = vm.uiState.first { !it.expandedGroupKeys.contains(key) }
        assertFalse(collapsedState.transactionClusters[0].isExpanded)
    }

    // Test 40: Ungrouped transactions cluster under "Other Expenses"
    @Test
    fun testUngroupedTransactionsClusterUnderOtherExpenses() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Personal Coffee", amountPaise = 25000L, expenseGroupId = null)
        )

        val vm = createViewModel()
        val state = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        assertEquals(1, state.transactionClusters.size)
        assertEquals("Other Expenses", state.transactionClusters[0].name)
        assertEquals("ungrouped", state.transactionClusters[0].key)
    }

    // Test 41: Mixed grouped and ungrouped
    @Test
    fun testMixedGroupedAndUngroupedClusters() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip A")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Flight", amountPaise = 400000L, expenseGroupId = 1L)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Daily Lunch", amountPaise = 30000L, expenseGroupId = null)
        )

        val vm = createViewModel()
        val state = vm.uiState.first { it.transactionClusters.size == 2 }
        val names = state.transactionClusters.map { it.name }.toSet()
        assertTrue(names.contains("Trip A"))
        assertTrue(names.contains("Other Expenses"))
    }

    // Test 42: Rename group updates transaction display
    @Test
    fun testRenameGroupUpdatesTransactionDisplayName() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip Initial")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Expense", amountPaise = 10000L, expenseGroupId = 1L, expenseGroupName = "Trip Initial")
        )

        val vm = createViewModel()
        val initial = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        assertEquals("Trip Initial", initial.transactionClusters[0].name)

        // Rename group via repository
        expenseGroupRepository.updateExpenseGroup(
            ExpenseGroup(id = 1L, name = "Trip Renamed")
        )

        val renamed = vm.uiState.first { it.transactionClusters.any { c -> c.name == "Trip Renamed" } }
        assertEquals("Trip Renamed", renamed.transactionClusters[0].name)
    }

    // Test 43: Archive group
    @Test
    fun testArchiveGroupRetainsTransactions() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Archived Trip", isArchived = false)
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Archived Item", amountPaise = 50000L, expenseGroupId = 1L, expenseGroupName = "Archived Trip")
        )

        expenseGroupRepository.setArchived(1L, true)

        val vm = createViewModel()
        val state = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        // Even if group is archived from active groups list, cluster retains named display
        assertEquals("Archived Trip", state.transactionClusters[0].name)
    }

    // Test 44: Delete group preserves transactions
    @Test
    fun testDeleteGroupPreservesTransactionsWithHistoricalName() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "To Delete")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Item", amountPaise = 50000L, expenseGroupId = 1L, expenseGroupName = "To Delete")
        )

        expenseGroupRepository.deleteExpenseGroup(1L)

        val allTxns = transactionRepository.getAllTransactions().first()
        assertEquals(1, allTxns.size)
        assertEquals("To Delete", allTxns[0].expenseGroupName)
        assertNull(allTxns[0].expenseGroupId)
    }

    // Test 45: Filtered group totals
    @Test
    fun testFilteredGroupTotalsReflectTypeFilter() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Expense Item", amountPaise = 50000L, expenseGroupId = 1L, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Trip Refund", amountPaise = 10000L, expenseGroupId = 1L, transactionType = TransactionType.REFUND)
        )

        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        vm.onTypeFilterSelected("EXPENSE")

        val state = vm.uiState.first { it.selectedTypeFilter == "EXPENSE" && it.transactionClusters.isNotEmpty() }
        val cluster = state.transactionClusters.first { it.name == "Trip" }
        assertEquals(1, cluster.transactionCount)
        assertEquals(50000L, cluster.totalPersonalExpensePaise)
    }

    // Test 46: Search by title
    @Test
    fun testSearchByTitleFiltersClusters() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Scuba Diving", amountPaise = 80000L, expenseGroupId = 1L)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Airport Taxi", amountPaise = 20000L, expenseGroupId = 1L)
        )

        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        vm.onSearchQueryChanged("Scuba")

        val state = vm.uiState.first { it.searchQuery == "Scuba" && it.transactionClusters.isNotEmpty() }
        val cluster = state.transactionClusters.first { it.name == "Trip" }
        assertEquals(1, cluster.transactionCount)
        assertEquals("Scuba Diving", cluster.transactions[0].title)
    }

    // Test 47: Search by group name
    @Test
    fun testSearchByGroupNameMatchesClusters() = runBlocking {
        val group1 = ExpenseGroupEntity(id = 1L, name = "Farm & Caves")
        val group2 = ExpenseGroupEntity(id = 2L, name = "Manali Trek")
        database.expenseGroupDao().insertExpenseGroup(group1)
        database.expenseGroupDao().insertExpenseGroup(group2)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Dinner", amountPaise = 50000L, expenseGroupId = 1L, expenseGroupName = "Farm & Caves")
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Tent", amountPaise = 70000L, expenseGroupId = 2L, expenseGroupName = "Manali Trek")
        )

        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        vm.onSearchQueryChanged("Caves")

        val state = vm.uiState.first { it.searchQuery == "Caves" && it.transactionClusters.isNotEmpty() }
        assertEquals(1, state.transactionClusters.size)
        assertEquals("Farm & Caves", state.transactionClusters[0].name)
    }

    // Test 48: Payment method filter
    @Test
    fun testPaymentMethodFilterUpdatesClusters() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "UPI Item", amountPaise = 30000L, paymentMethod = PaymentMethod.UPI, expenseGroupId = 1L)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Cash Item", amountPaise = 15000L, paymentMethod = PaymentMethod.CASH, expenseGroupId = 1L)
        )

        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        vm.onPaymentMethodFilterSelected(PaymentMethod.CASH)

        val state = vm.uiState.first { it.selectedPaymentMethodFilter == PaymentMethod.CASH && it.transactionClusters.isNotEmpty() }
        val cluster = state.transactionClusters.first { it.name == "Trip" }
        assertEquals(1, cluster.transactionCount)
        assertEquals(PaymentMethod.CASH, cluster.transactions[0].paymentMethod)
    }

    // Test 49: Date filter updates clusters
    @Test
    fun testDateFilterUpdatesClusters() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Recent Item", amountPaise = 50000L, transactionDate = fixedTimestamp, expenseGroupId = 1L)
        )

        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        vm.onDateFilterSelected("ALL")

        val state = vm.uiState.first { !it.isLoading && it.transactionClusters.isNotEmpty() }
        assertEquals(1, state.transactionClusters.size)
    }

    // Test 50: Ignored transactions in clusters
    @Test
    fun testIgnoredTransactionsExcludedFromActiveClusters() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Active Item", amountPaise = 50000L, expenseGroupId = 1L, transactionStatus = TransactionStatus.ACTIVE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Ignored Item", amountPaise = 20000L, expenseGroupId = 1L, transactionStatus = TransactionStatus.IGNORED)
        )

        val vm = createViewModel()
        vm.uiState.first { !it.isLoading }
        vm.onTypeFilterSelected("ALL")

        val state = vm.uiState.first { it.selectedTypeFilter == "ALL" && it.transactionClusters.isNotEmpty() }
        val cluster = state.transactionClusters.first { it.name == "Trip" }
        // Default ALL filter matches ACTIVE status
        assertEquals(1, cluster.transactionCount)
        assertEquals("Active Item", cluster.transactions[0].title)
    }

    // Test 51: Refunds in group
    @Test
    fun testRefundsInGroupDoNotInflatePersonalExpense() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Hotel", amountPaise = 50000L, expenseGroupId = 1L, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Hotel Refund", amountPaise = 20000L, expenseGroupId = 1L, transactionType = TransactionType.REFUND)
        )

        val vm = createViewModel()
        val state = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        val cluster = state.transactionClusters.first { it.name == "Trip" }
        // Expense calculation only counts EXPENSE and UPI_LITE_PAYMENT
        assertEquals(50000L, cluster.totalPersonalExpensePaise)
    }

    // Test 52: Transfers in group
    @Test
    fun testTransfersInGroupExcludedFromPersonalExpenseTotal() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Transfer to Wallet", amountPaise = 100000L, expenseGroupId = 1L, transactionType = TransactionType.TRANSFER)
        )

        val vm = createViewModel()
        val state = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        val cluster = state.transactionClusters.first { it.name == "Trip" }
        assertEquals(0L, cluster.totalPersonalExpensePaise)
    }

    // Test 53: Transaction edit updates cluster total
    @Test
    fun testTransactionEditUpdatesClusterTotal() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        val txn = Transaction(id = 1L, title = "Initial", amountPaise = 30000L, expenseGroupId = 1L)
        transactionRepository.saveTransaction(txn)

        val vm = createViewModel()
        val initial = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        assertEquals(30000L, initial.transactionClusters[0].totalPersonalExpensePaise)

        // Update transaction amount
        transactionRepository.saveTransaction(txn.copy(amountPaise = 60000L))
        val updated = vm.uiState.first { it.transactionClusters[0].totalPersonalExpensePaise == 60000L }
        assertEquals(60000L, updated.transactionClusters[0].totalPersonalExpensePaise)
    }

    // Test 54: Transaction deletion removes from cluster
    @Test
    fun testTransactionDeletionRemovesFromCluster() = runBlocking {
        val group = ExpenseGroupEntity(id = 1L, name = "Trip")
        database.expenseGroupDao().insertExpenseGroup(group)

        val txn = Transaction(id = 1L, title = "To Delete", amountPaise = 30000L, expenseGroupId = 1L)
        transactionRepository.saveTransaction(txn)

        val vm = createViewModel()
        val initial = vm.uiState.first { it.transactionClusters.isNotEmpty() }
        assertEquals(1, initial.transactionClusters[0].transactionCount)

        transactionRepository.deleteTransaction(1L)
        val emptyState = vm.uiState.first { it.transactionClusters.isEmpty() }
        assertTrue(emptyState.transactionClusters.isEmpty())
    }

    // Test 55: Expand all and collapse all
    @Test
    fun testExpandAllAndCollapseAll() = runBlocking {
        val group1 = ExpenseGroupEntity(id = 1L, name = "Trip 1")
        val group2 = ExpenseGroupEntity(id = 2L, name = "Trip 2")
        database.expenseGroupDao().insertExpenseGroup(group1)
        database.expenseGroupDao().insertExpenseGroup(group2)

        transactionRepository.saveTransaction(Transaction(id = 1L, title = "A", amountPaise = 1000L, expenseGroupId = 1L))
        transactionRepository.saveTransaction(Transaction(id = 2L, title = "B", amountPaise = 2000L, expenseGroupId = 2L))

        val vm = createViewModel()
        vm.uiState.first { it.transactionClusters.size == 2 }

        vm.expandAllGroups()
        val expanded = vm.uiState.first { it.expandedGroupKeys.size == 2 }
        assertEquals(2, expanded.expandedGroupKeys.size)
        assertTrue(expanded.transactionClusters.all { it.isExpanded })

        vm.collapseAllGroups()
        val collapsed = vm.uiState.first { it.expandedGroupKeys.isEmpty() }
        assertTrue(collapsed.expandedGroupKeys.isEmpty())
        assertTrue(collapsed.transactionClusters.none { it.isExpanded })
    }

    // Test 56: Grouping mode toggle
    @Test
    fun testGroupingModeToggle() = runBlocking {
        val vm = createViewModel()
        assertTrue("Default grouping mode must be grouped", vm.uiState.value.isGroupedByExpenseGroup)

        vm.setGroupingMode(false)
        assertFalse(vm.uiState.value.isGroupedByExpenseGroup)

        vm.setGroupingMode(true)
        assertTrue(vm.uiState.value.isGroupedByExpenseGroup)
    }
}

