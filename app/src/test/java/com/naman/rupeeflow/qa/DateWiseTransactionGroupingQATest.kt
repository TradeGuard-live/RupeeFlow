package com.naman.rupeeflow.qa

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.ExpenseGroupEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.ui.screens.transactions.TransactionDateGrouper
import com.naman.rupeeflow.ui.screens.transactions.TransactionListItem
import com.naman.rupeeflow.ui.screens.transactions.TransactionsViewModel
import com.naman.rupeeflow.ui.theme.DarkAppColors
import com.naman.rupeeflow.ui.theme.PeachyAppColors
import kotlinx.coroutines.Dispatchers
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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DateWiseTransactionGroupingQATest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var expenseGroupRepository: ExpenseGroupRepository

    // Fixed reference timestamp: Wednesday, 18 November 2026 12:00:00 UTC
    private val fixedNow = 1795003200000L
    private val utcTimeZone = TimeZone.getTimeZone("UTC")
    private val usLocale = Locale.US

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
            transactionDao = database.transactionDao(),
            friendGroupDao = database.friendGroupDao()
        )

        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#EF4444")
            )
            database.categoryDao().insertCategory(
                CategoryEntity(id = 2L, name = "Travel", iconName = "flight", colorHex = "#3B82F6")
            )
            database.friendDao().insertFriend(FriendEntity(id = 10L, name = "Alice"))
            database.friendDao().insertFriend(FriendEntity(id = 20L, name = "Bob"))
        }
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun createTransactionsViewModel(
        clock: () -> Long = { fixedNow },
        timeZoneProvider: () -> TimeZone = { utcTimeZone }
    ): TransactionsViewModel {
        return TransactionsViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            friendGroupRepository = null,
            expenseGroupRepository = expenseGroupRepository,
            clock = clock,
            timeZoneProvider = timeZoneProvider
        )
    }

    // 1. Today's heading
    @Test
    fun test01_todaysHeading() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Lunch", amountPaise = 25000L, transactionDate = fixedNow)
        )
        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.isNotEmpty() }
        assertEquals(1, state.dateGroups.size)
        assertEquals("Today", state.dateGroups[0].heading)
        assertEquals(1, state.dateGroups[0].items.size)
    }

    // 2. Yesterday's heading
    @Test
    fun test02_yesterdaysHeading() = runBlocking {
        val yesterdayTimestamp = fixedNow - 86400000L // 17 Nov 2026
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Groceries", amountPaise = 50000L, transactionDate = yesterdayTimestamp)
        )
        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.isNotEmpty() }
        assertEquals(1, state.dateGroups.size)
        assertEquals("Yesterday", state.dateGroups[0].heading)
    }

    // 3. Older date headings
    @Test
    fun test03_olderDateHeadings() = runBlocking {
        // 15 November 2026 12:00:00 UTC (3 days before fixedNow)
        val olderTimestamp = fixedNow - (86400000L * 3)
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Book", amountPaise = 40000L, transactionDate = olderTimestamp)
        )
        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.isNotEmpty() }
        assertEquals(1, state.dateGroups.size)
        assertEquals("15 November 2026", state.dateGroups[0].heading)
    }

    // 4. Multiple transactions on one date
    @Test
    fun test04_multipleTransactionsOnOneDate() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Breakfast", amountPaise = 10000L, transactionDate = fixedNow - 3600000L)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Coffee", amountPaise = 15000L, transactionDate = fixedNow - 1800000L)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 3L, title = "Dinner", amountPaise = 45000L, transactionDate = fixedNow)
        )
        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.isNotEmpty() && it.dateGroups[0].items.size == 3 }
        assertEquals(1, state.dateGroups.size)
        assertEquals("Today", state.dateGroups[0].heading)
        assertEquals(3, state.dateGroups[0].items.size)
    }

    // 5. Transactions across different dates
    @Test
    fun test05_transactionsAcrossDifferentDates() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Today Item", amountPaise = 10000L, transactionDate = fixedNow)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Yesterday Item", amountPaise = 20000L, transactionDate = fixedNow - 86400000L)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 3L, title = "Past Item", amountPaise = 30000L, transactionDate = fixedNow - (86400000L * 5))
        )
        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.size == 3 }
        assertEquals("Today", state.dateGroups[0].heading)
        assertEquals("Yesterday", state.dateGroups[1].heading)
        assertEquals("13 November 2026", state.dateGroups[2].heading)
    }

    // 6. Newest date first
    @Test
    fun test06_newestDateFirst() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Day 1", amountPaise = 10L, transactionDate = fixedNow - (86400000L * 10))
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Day 2", amountPaise = 20L, transactionDate = fixedNow - (86400000L * 2))
        )
        transactionRepository.saveTransaction(
            Transaction(id = 3L, title = "Day 3", amountPaise = 30L, transactionDate = fixedNow)
        )
        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.size == 3 }
        assertTrue(state.dateGroups[0].dateEpochMillis > state.dateGroups[1].dateEpochMillis)
        assertTrue(state.dateGroups[1].dateEpochMillis > state.dateGroups[2].dateEpochMillis)
        assertEquals("Today", state.dateGroups[0].heading)
    }

    // 7. Newest time first
    @Test
    fun test07_newestTimeFirst() = runBlocking {
        val tMorning = fixedNow - 7200000L
        val tNoon = fixedNow - 3600000L
        val tEvening = fixedNow
        transactionRepository.saveTransaction(Transaction(id = 1L, title = "Morning", amountPaise = 10L, transactionDate = tMorning))
        transactionRepository.saveTransaction(Transaction(id = 2L, title = "Evening", amountPaise = 20L, transactionDate = tEvening))
        transactionRepository.saveTransaction(Transaction(id = 3L, title = "Noon", amountPaise = 30L, transactionDate = tNoon))

        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.isNotEmpty() && it.dateGroups[0].items.size == 3 }
        val items = state.dateGroups[0].items.filterIsInstance<TransactionListItem.Individual>()
        assertEquals(2L, items[0].transaction.id) // Evening (newest)
        assertEquals(3L, items[1].transaction.id) // Noon
        assertEquals(1L, items[2].transaction.id) // Morning (oldest)
    }

    // 8. Same timestamp ordering
    @Test
    fun test08_sameTimestampOrdering() = runBlocking {
        val identicalTime = fixedNow
        transactionRepository.saveTransaction(Transaction(id = 5L, title = "First Added", amountPaise = 10L, transactionDate = identicalTime))
        transactionRepository.saveTransaction(Transaction(id = 15L, title = "Second Added", amountPaise = 20L, transactionDate = identicalTime))

        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.isNotEmpty() && it.dateGroups[0].items.size == 2 }
        val items = state.dateGroups[0].items.filterIsInstance<TransactionListItem.Individual>()
        // Deterministic ordering breaks tie by ID descending: 15L first, then 5L
        assertEquals(15L, items[0].transaction.id)
        assertEquals(5L, items[1].transaction.id)
    }

    // 9. Midnight boundary
    @Test
    fun test09_midnightBoundary() = runBlocking {
        // Today is 18 Nov 2026.
        // Yesterday late night: 17 Nov 2026 23:59:59 UTC
        val calYesterdayEnd = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2026, Calendar.NOVEMBER, 17, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }
        // Today early morning: 18 Nov 2026 00:00:01 UTC
        val calTodayStart = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2026, Calendar.NOVEMBER, 18, 0, 0, 1)
            set(Calendar.MILLISECOND, 0)
        }
        val calNowMidday = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2026, Calendar.NOVEMBER, 18, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Late Night", amountPaise = 100L, transactionDate = calYesterdayEnd.timeInMillis)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Early Morning", amountPaise = 200L, transactionDate = calTodayStart.timeInMillis)
        )

        val vm = createTransactionsViewModel(clock = { calNowMidday.timeInMillis })
        val state = vm.uiState.first { it.dateGroups.size == 2 }
        assertEquals("Today", state.dateGroups[0].heading)
        assertEquals(2L, (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.id)
        assertEquals("Yesterday", state.dateGroups[1].heading)
        assertEquals(1L, (state.dateGroups[1].items[0] as TransactionListItem.Individual).transaction.id)
    }

    // 10. Month boundary
    @Test
    fun test10_monthBoundary() = runBlocking {
        // Now is Nov 1, 2026 10:00:00 UTC
        val calNow = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2026, Calendar.NOVEMBER, 1, 10, 0, 0)
        }
        // Oct 31, 2026 23:50:00 UTC
        val calOctEnd = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2026, Calendar.OCTOBER, 31, 23, 50, 0)
        }
        val headingOct = TransactionDateGrouper.formatDateHeading(calOctEnd.timeInMillis, calNow.timeInMillis, utcTimeZone, usLocale)
        val headingNov = TransactionDateGrouper.formatDateHeading(calNow.timeInMillis, calNow.timeInMillis, utcTimeZone, usLocale)

        assertEquals("Yesterday", headingOct)
        assertEquals("Today", headingNov)
    }

    // 11. Year boundary
    @Test
    fun test11_yearBoundary() = runBlocking {
        // Now is Jan 1, 2027 08:00:00 UTC
        val calNow = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2027, Calendar.JANUARY, 1, 8, 0, 0)
        }
        // Dec 31, 2026 23:30:00 UTC
        val calDecEnd = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2026, Calendar.DECEMBER, 31, 23, 30, 0)
        }
        val headingDec = TransactionDateGrouper.formatDateHeading(calDecEnd.timeInMillis, calNow.timeInMillis, utcTimeZone, usLocale)
        val headingJan = TransactionDateGrouper.formatDateHeading(calNow.timeInMillis, calNow.timeInMillis, utcTimeZone, usLocale)

        assertEquals("Yesterday", headingDec)
        assertEquals("Today", headingJan)
    }

    // 12. Leap-year dates
    @Test
    fun test12_leapYearDates() {
        val calLeap = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2028, Calendar.FEBRUARY, 29, 14, 0, 0)
        }
        val calLater = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2028, Calendar.MARCH, 15, 12, 0, 0)
        }
        val heading = TransactionDateGrouper.formatDateHeading(calLeap.timeInMillis, calLater.timeInMillis, utcTimeZone, usLocale)
        assertEquals("29 February 2028", heading)
    }

    // 13. Local timezone grouping
    @Test
    fun test13_localTimezoneGrouping() {
        // Timestamp 1795028400000L is 18 Nov 2026 19:00:00 UTC.
        // In UTC: calendar date is 18 Nov 2026.
        // In Tokyo (+9 hours): it is 19 Nov 2026 04:00:00 (Next day!).
        val targetTs = 1795028400000L
        val tokyoTz = TimeZone.getTimeZone("Asia/Tokyo")

        val utcCal = Calendar.getInstance(utcTimeZone, usLocale).apply { timeInMillis = targetTs }
        val tokyoCal = Calendar.getInstance(tokyoTz, usLocale).apply { timeInMillis = targetTs }

        assertEquals(18, utcCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, tokyoCal.get(Calendar.DAY_OF_MONTH))

        val startDayUtc = TransactionDateGrouper.getStartOfCalendarDay(targetTs, utcTimeZone, usLocale)
        val startDayTokyo = TransactionDateGrouper.getStartOfCalendarDay(targetTs, tokyoTz, usLocale)
        assertNotEquals(startDayUtc, startDayTokyo)
    }

    // 14. Timezone change
    @Test
    fun test14_timezoneChange() = runBlocking {
        val targetTs = 1795028400000L // 18 Nov 19:00 UTC -> 19 Nov 04:00 Tokyo
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Dinner", amountPaise = 5000L, transactionDate = targetTs)
        )
        // In UTC, with now = 18 Nov 19:00 UTC
        val vmUtc = createTransactionsViewModel(clock = { targetTs }, timeZoneProvider = { utcTimeZone })
        val stateUtc = vmUtc.uiState.first { it.dateGroups.isNotEmpty() }
        assertEquals("Today", stateUtc.dateGroups[0].heading)

        // In Tokyo, with now = 18 Nov 19:00 UTC (which is 19 Nov 04:00 Tokyo)
        val tokyoTz = TimeZone.getTimeZone("Asia/Tokyo")
        val vmTokyo = createTransactionsViewModel(clock = { targetTs }, timeZoneProvider = { tokyoTz })
        val stateTokyo = vmTokyo.uiState.first { it.dateGroups.isNotEmpty() }
        assertEquals("Today", stateTokyo.dateGroups[0].heading)
        val startTokyo = TransactionDateGrouper.getStartOfCalendarDay(targetTs, tokyoTz, usLocale)
        assertEquals(startTokyo, stateTokyo.dateGroups[0].dateEpochMillis)
    }

    // 15. Historical transactions
    @Test
    fun test15_historicalTransactions() {
        val calHistorical = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2020, Calendar.JUNE, 15, 10, 0, 0)
        }
        val heading = TransactionDateGrouper.formatDateHeading(calHistorical.timeInMillis, fixedNow, utcTimeZone, usLocale)
        assertEquals("15 June 2020", heading)
    }

    // 16. Future-dated transactions
    @Test
    fun test16_futureDatedTransactions() = runBlocking {
        val futureTs = fixedNow + (86400000L * 7) // 25 Nov 2026
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Future Flight", amountPaise = 500000L, transactionDate = futureTs)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Today Coffee", amountPaise = 20000L, transactionDate = fixedNow)
        )

        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.size == 2 }
        assertEquals("25 November 2026", state.dateGroups[0].heading)
        assertEquals("Today", state.dateGroups[1].heading)
    }

    // 17. Editing transaction date
    @Test
    fun test17_editingTransactionDate() = runBlocking {
        val txn = Transaction(id = 1L, title = "Lunch", amountPaise = 25000L, transactionDate = fixedNow)
        transactionRepository.saveTransaction(txn)

        val vm = createTransactionsViewModel()
        var state = vm.uiState.first { it.dateGroups.isNotEmpty() }
        assertEquals("Today", state.dateGroups[0].heading)

        // Edit transaction date to 5 days ago (13 Nov 2026)
        val updatedTxn = txn.copy(transactionDate = fixedNow - (86400000L * 5))
        vm.saveTransaction(updatedTxn)

        state = vm.uiState.first { it.dateGroups.isNotEmpty() && it.dateGroups[0].heading == "13 November 2026" }
        assertEquals(1, state.dateGroups.size)
        assertEquals("13 November 2026", state.dateGroups[0].heading)
    }

    // 18. Deleting the last transaction in a group
    @Test
    fun test18_deletingTheLastTransactionInAGroup() = runBlocking {
        val txnToday = Transaction(id = 1L, title = "Coffee", amountPaise = 15000L, transactionDate = fixedNow)
        val txnYesterday = Transaction(id = 2L, title = "Dinner", amountPaise = 40000L, transactionDate = fixedNow - 86400000L)
        transactionRepository.saveTransaction(txnToday)
        transactionRepository.saveTransaction(txnYesterday)

        val vm = createTransactionsViewModel()
        var state = vm.uiState.first { it.dateGroups.size == 2 }
        assertEquals(2, state.dateGroups.size)

        // Delete today's transaction
        vm.deleteTransaction(txnToday)

        state = vm.uiState.first { it.dateGroups.size == 1 }
        assertEquals("Yesterday", state.dateGroups[0].heading)
        assertEquals(1, state.dateGroups.size)
    }

    // 19. Empty groups not displayed
    @Test
    fun test19_emptyGroupsNotDisplayed() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Nov 18", amountPaise = 100L, transactionDate = fixedNow)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Nov 10", amountPaise = 200L, transactionDate = fixedNow - (86400000L * 8))
        )

        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.size == 2 }
        // Exactly 2 date groups for the 2 days with data. No empty intermediate groups.
        assertEquals(2, state.dateGroups.size)
        assertEquals("Today", state.dateGroups[0].heading)
        assertEquals("10 November 2026", state.dateGroups[1].heading)
    }

    // 20. Search result grouping
    @Test
    fun test20_searchResultGrouping() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Apple Store", amountPaise = 100000L, transactionDate = fixedNow)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Banana Shake", amountPaise = 15000L, transactionDate = fixedNow)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 3L, title = "Apple Pie", amountPaise = 25000L, transactionDate = fixedNow - 86400000L)
        )

        val vm = createTransactionsViewModel()
        vm.uiState.first { it.allTransactions.size == 3 }
        vm.onSearchQueryChanged("Apple")

        val state = vm.uiState.first { it.searchQuery == "Apple" }
        assertEquals(2, state.dateGroups.size)
        assertEquals(1, state.dateGroups[0].items.size)
        assertEquals("Apple Store", (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.title)
        assertEquals(1, state.dateGroups[1].items.size)
        assertEquals("Apple Pie", (state.dateGroups[1].items[0] as TransactionListItem.Individual).transaction.title)
    }

    // 21. Expense filter grouping
    @Test
    fun test21_expenseFilterGrouping() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Dinner", amountPaise = 40000L, transactionDate = fixedNow, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Salary", amountPaise = 5000000L, transactionDate = fixedNow, transactionType = TransactionType.INCOME)
        )

        val vm = createTransactionsViewModel()
        vm.uiState.first { it.allTransactions.size == 2 }
        vm.onTypeFilterSelected("EXPENSE")

        val state = vm.uiState.first { it.selectedTypeFilter == "EXPENSE" }
        assertEquals(1, state.dateGroups.size)
        assertEquals(1, state.dateGroups[0].items.size)
        assertEquals("Dinner", (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.title)
    }

    // 22. Income filter grouping
    @Test
    fun test22_incomeFilterGrouping() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Dinner", amountPaise = 40000L, transactionDate = fixedNow, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Salary", amountPaise = 5000000L, transactionDate = fixedNow, transactionType = TransactionType.INCOME)
        )

        val vm = createTransactionsViewModel()
        vm.uiState.first { it.allTransactions.size == 2 }
        vm.onTypeFilterSelected("INCOME")

        val state = vm.uiState.first { it.selectedTypeFilter == "INCOME" }
        assertEquals(1, state.dateGroups.size)
        assertEquals(1, state.dateGroups[0].items.size)
        assertEquals("Salary", (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.title)
    }

    // 23. Refund filter grouping
    @Test
    fun test23_refundFilterGrouping() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Refund Item", amountPaise = 12000L, transactionDate = fixedNow, transactionType = TransactionType.REFUND)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Expense Item", amountPaise = 20000L, transactionDate = fixedNow, transactionType = TransactionType.EXPENSE)
        )

        val vm = createTransactionsViewModel()
        vm.uiState.first { it.allTransactions.size == 2 }
        vm.onTypeFilterSelected("REFUND")

        val state = vm.uiState.first { it.selectedTypeFilter == "REFUND" }
        assertEquals(1, state.dateGroups.size)
        assertEquals("Refund Item", (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.title)
    }

    // 24. Transfer filter grouping
    @Test
    fun test24_transferFilterGrouping() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Bank Transfer", amountPaise = 100000L, transactionDate = fixedNow, transactionType = TransactionType.TRANSFER)
        )
        val vm = createTransactionsViewModel()
        vm.uiState.first { it.allTransactions.size == 1 }
        vm.onTypeFilterSelected("TRANSFER")

        val state = vm.uiState.first { it.selectedTypeFilter == "TRANSFER" }
        assertEquals(1, state.dateGroups.size)
        assertEquals("Bank Transfer", (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.title)
    }

    // 25. Ignored transaction grouping
    @Test
    fun test25_ignoredTransactionGrouping() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Test Ignored", amountPaise = 100L, transactionDate = fixedNow, transactionStatus = TransactionStatus.IGNORED)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Active Item", amountPaise = 200L, transactionDate = fixedNow, transactionStatus = TransactionStatus.ACTIVE)
        )

        val vm = createTransactionsViewModel()
        vm.uiState.first { it.allTransactions.size == 2 }
        vm.onTypeFilterSelected("IGNORED")

        val state = vm.uiState.first { it.selectedTypeFilter == "IGNORED" }
        assertEquals(1, state.dateGroups.size)
        assertEquals("Test Ignored", (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.title)
    }

    // 26. Payment method filter grouping
    @Test
    fun test26_paymentMethodFilterGrouping() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "UPI Pay", amountPaise = 1000L, transactionDate = fixedNow, paymentMethod = PaymentMethod.UPI)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Card Pay", amountPaise = 2000L, transactionDate = fixedNow, paymentMethod = PaymentMethod.CREDIT_CARD)
        )

        val vm = createTransactionsViewModel()
        vm.uiState.first { it.allTransactions.size == 2 }
        vm.onPaymentMethodFilterSelected(PaymentMethod.UPI)

        val state = vm.uiState.first { it.selectedPaymentMethodFilter == PaymentMethod.UPI }
        assertEquals(1, state.dateGroups.size)
        assertEquals("UPI Pay", (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.title)
    }

    // 27. Monthly filter grouping
    @Test
    fun test27_monthlyFilterGrouping() = runBlocking {
        // fixedNow is Nov 18, 2026.
        // Also add an Oct 2026 transaction.
        val calOct = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2026, Calendar.OCTOBER, 15, 12, 0, 0)
        }
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Nov Item", amountPaise = 1000L, transactionDate = fixedNow)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Oct Item", amountPaise = 2000L, transactionDate = calOct.timeInMillis)
        )

        val vm = createTransactionsViewModel()
        vm.uiState.first { it.allTransactions.size == 2 }
        vm.onDateFilterSelected("MONTHLY")

        val state = vm.uiState.first { it.selectedDateFilter == "MONTHLY" }
        // For monthly offset 0 (November 2026), only Nov item is included
        assertEquals(1, state.dateGroups.size)
        assertEquals("Nov Item", (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.title)
    }

    // 28. Weekly filter grouping
    @Test
    fun test28_weeklyFilterGrouping() = runBlocking {
        // fixedNow is Wednesday, Nov 18, 2026.
        // Add a transaction on Monday, Nov 16, 2026 (same week).
        // Add a transaction on Sunday, Nov 8, 2026 (previous week).
        val calMon = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2026, Calendar.NOVEMBER, 16, 12, 0, 0)
        }
        val calPrevWeek = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(2026, Calendar.NOVEMBER, 8, 12, 0, 0)
        }
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "This Week", amountPaise = 1000L, transactionDate = calMon.timeInMillis)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Prev Week", amountPaise = 2000L, transactionDate = calPrevWeek.timeInMillis)
        )

        val vm = createTransactionsViewModel()
        vm.uiState.first { it.allTransactions.size == 2 }
        vm.onDateFilterSelected("WEEKLY")

        val state = vm.uiState.first { it.selectedDateFilter == "WEEKLY" }
        assertEquals(1, state.dateGroups.size)
        assertEquals("This Week", (state.dateGroups[0].items[0] as TransactionListItem.Individual).transaction.title)
    }

    // 29. Trip card grouping
    @Test
    fun test29_tripCardGrouping() = runBlocking {
        database.expenseGroupDao().insertExpenseGroup(
            ExpenseGroupEntity(id = 1L, name = "Goa Trip", destination = "Goa")
        )
        // Constituent transactions: one on Nov 16, one on Nov 18 (fixedNow)
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Flight", amountPaise = 500000L, transactionDate = fixedNow - (86400000L * 2), expenseGroupId = 1L)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Hotel", amountPaise = 300000L, transactionDate = fixedNow, expenseGroupId = 1L)
        )
        // Standalone transaction
        transactionRepository.saveTransaction(
            Transaction(id = 3L, title = "Personal Coffee", amountPaise = 25000L, transactionDate = fixedNow - 86400000L)
        )

        val vm = createTransactionsViewModel()
        val state = vm.uiState.first { it.dateGroups.size == 2 }

        // Group 0: Today (Trip card latest date is fixedNow)
        assertEquals("Today", state.dateGroups[0].heading)
        assertEquals(1, state.dateGroups[0].items.size)
        assertTrue(state.dateGroups[0].items[0] is TransactionListItem.TripCard)
        val tripCard = state.dateGroups[0].items[0] as TransactionListItem.TripCard
        assertEquals("Goa Trip", tripCard.name)
        assertEquals(2, tripCard.transactionCount)

        // Group 1: Yesterday (Personal Coffee)
        assertEquals("Yesterday", state.dateGroups[1].heading)
        assertEquals(1, state.dateGroups[1].items.size)
        assertTrue(state.dateGroups[1].items[0] is TransactionListItem.Individual)

        // Verify no duplicate individual trip transactions in the list
        val allIndividuals = state.dateGroups.flatMap { it.items }.filterIsInstance<TransactionListItem.Individual>()
        assertEquals(1, allIndividuals.size)
        assertEquals("Personal Coffee", allIndividuals[0].transaction.title)
    }

    // 30. Large transaction history performance
    @Test
    fun test30_largeTransactionHistoryPerformance() {
        val largeList = (1..600).map { i ->
            val ts = fixedNow - (i * 3600000L)
            TransactionListItem.Individual(
                Transaction(
                    id = i.toLong(),
                    title = "Transaction $i",
                    amountPaise = (i * 100).toLong(),
                    transactionDate = ts
                )
            )
        }

        val startTime = System.currentTimeMillis()
        val grouped = TransactionDateGrouper.groupItemsByDate(largeList, fixedNow, utcTimeZone, usLocale)
        val duration = System.currentTimeMillis() - startTime

        assertTrue(grouped.isNotEmpty())
        assertEquals("Today", grouped[0].heading)
        // Grouping 600 items should easily complete well under 100ms
        assertTrue("Expected performance < 100ms, actual: ${duration}ms", duration < 100)
    }

    // 31. Dark theme
    @Test
    fun test31_darkTheme() {
        val dark = DarkAppColors
        assertNotNull(dark.background)
        assertNotNull(dark.surface)
        assertNotNull(dark.cardBorder)
        assertNotNull(dark.textPrimary)
        assertNotNull(dark.textSecondary)
        assertNotNull(dark.divider)
    }

    // 32. Peachy Cozy theme
    @Test
    fun test32_peachyCozyTheme() {
        val peachy = PeachyAppColors
        assertNotNull(peachy.background)
        assertNotNull(peachy.surface)
        assertNotNull(peachy.primaryAccent)
        assertNotNull(peachy.cardBorder)
        assertNotNull(peachy.textPrimary)
        assertNotNull(peachy.textSecondary)
        assertNotNull(peachy.divider)
    }

    // 33. Existing financial calculations regression
    @Test
    fun test33_existingFinancialCalculationsRegression() {
        val t1 = Transaction(id = 1L, title = "Groceries", amountPaise = 100000L, transactionType = TransactionType.EXPENSE, transactionDate = fixedNow)
        val t2 = Transaction(id = 2L, title = "Salary", amountPaise = 5000000L, transactionType = TransactionType.INCOME, transactionDate = fixedNow)
        val t3 = Transaction(id = 3L, title = "Refund", amountPaise = 20000L, transactionType = TransactionType.REFUND, transactionDate = fixedNow)

        val txns = listOf(t1, t2, t3)
        val totalPersonal = PersonalFinancialAggregator.calculateTotalPersonalExpense(txns)
        assertEquals(100000L, totalPersonal)

        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = txns,
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = fixedNow - (86400000L * 10),
            now = fixedNow
        )
        assertEquals(5000000L, summary.totalIncomeThisMonthPaise)
        assertEquals(100000L, summary.totalExpenseThisMonthPaise)
        assertEquals(20000L, summary.totalRefundThisMonthPaise)
        assertEquals(80000L, summary.netSpendingPaise)
    }
}
