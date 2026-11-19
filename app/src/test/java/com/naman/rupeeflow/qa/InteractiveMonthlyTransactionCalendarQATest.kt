package com.naman.rupeeflow.qa

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.ExpenseGroupEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.screens.calendar.MonthlyCalendarEngine
import com.naman.rupeeflow.ui.screens.calendar.TransactionsViewMode
import com.naman.rupeeflow.ui.screens.transactions.TransactionsViewModel
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
class InteractiveMonthlyTransactionCalendarQATest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var expenseGroupRepository: ExpenseGroupRepository

    // Reference timestamp: Wednesday, 18 November 2026 12:00:00 UTC
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
        try {
            if (::database.isInitialized) {
                database.close()
            }
        } catch (_: Exception) {}
        try {
            Dispatchers.resetMain()
        } catch (_: Exception) {}
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
            inputRankingRepository = null,
            pendingTransactionRepository = null,
            clock = clock,
            timeZoneProvider = timeZoneProvider
        )
    }

    private fun makeTimestamp(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        val cal = Calendar.getInstance(utcTimeZone, usLocale).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    // 1. Month Grid Dimensions
    @Test
    fun test01_monthGridDimensions() {
        val grid = MonthlyCalendarEngine.computeMonthGrid(
            year = 2026,
            month = Calendar.NOVEMBER,
            transactions = emptyList(),
            timeZone = utcTimeZone,
            locale = usLocale
        )
        assertEquals(7 * grid.totalRowCount, grid.cells.size)
        assertEquals(30, grid.daysInMonth)
        assertTrue(grid.totalRowCount >= 4)
    }

    // 2. February Leap Year Calculation
    @Test
    fun test02_februaryLeapYearCalculation() {
        assertTrue(MonthlyCalendarEngine.isLeapYear(2024))
        assertTrue(MonthlyCalendarEngine.isLeapYear(2028))
        assertTrue(MonthlyCalendarEngine.isLeapYear(2000))
        assertFalse(MonthlyCalendarEngine.isLeapYear(2025))
        assertFalse(MonthlyCalendarEngine.isLeapYear(2026))
        assertFalse(MonthlyCalendarEngine.isLeapYear(2100))

        assertEquals(29, MonthlyCalendarEngine.getDaysInMonth(2024, Calendar.FEBRUARY))
        assertEquals(28, MonthlyCalendarEngine.getDaysInMonth(2026, Calendar.FEBRUARY))
    }

    // 3. Month Day Count for Standard Months
    @Test
    fun test03_monthDayCountStandardMonths() {
        val months31 = listOf(
            Calendar.JANUARY, Calendar.MARCH, Calendar.MAY, Calendar.JULY,
            Calendar.AUGUST, Calendar.OCTOBER, Calendar.DECEMBER
        )
        val months30 = listOf(
            Calendar.APRIL, Calendar.JUNE, Calendar.SEPTEMBER, Calendar.NOVEMBER
        )

        for (m in months31) {
            assertEquals(31, MonthlyCalendarEngine.getDaysInMonth(2026, m))
        }
        for (m in months30) {
            assertEquals(30, MonthlyCalendarEngine.getDaysInMonth(2026, m))
        }
    }

    // 4. Monday-First Leading Blank Calculation
    @Test
    fun test04_mondayFirstLeadingBlankCalculation() {
        // Nov 1, 2026 is a Sunday -> 6 leading blanks
        val novBlanks = MonthlyCalendarEngine.getLeadingBlankCount(2026, Calendar.NOVEMBER, utcTimeZone, usLocale)
        assertEquals(6, novBlanks)

        // Dec 1, 2026 is a Tuesday -> 1 leading blank
        val decBlanks = MonthlyCalendarEngine.getLeadingBlankCount(2026, Calendar.DECEMBER, utcTimeZone, usLocale)
        assertEquals(1, decBlanks)

        // Feb 1, 2027 is a Monday -> 0 leading blanks
        val febBlanks = MonthlyCalendarEngine.getLeadingBlankCount(2027, Calendar.FEBRUARY, utcTimeZone, usLocale)
        assertEquals(0, febBlanks)
    }

    // 5. Trailing Blank Count Calculation
    @Test
    fun test05_trailingBlankCountCalculation() {
        val grid = MonthlyCalendarEngine.computeMonthGrid(
            year = 2026,
            month = Calendar.NOVEMBER,
            transactions = emptyList(),
            timeZone = utcTimeZone,
            locale = usLocale
        )
        // Nov 2026: 6 leading + 30 days = 36 cells. 36 % 7 = 1. Trailing = (7 - 1) % 7 = 6. Total = 42 (6 rows).
        assertEquals(6, grid.leadingBlankCount)
        assertEquals(6, grid.trailingBlankCount)
        assertEquals(42, grid.cells.size)
        assertEquals(6, grid.totalRowCount)
    }

    // 6. Month Navigation Previous Month Regular
    @Test
    fun test06_monthNavigationPreviousMonthRegular() {
        val (year, month) = MonthlyCalendarEngine.getPreviousMonth(2026, Calendar.MAY)
        assertEquals(2026, year)
        assertEquals(Calendar.APRIL, month)
    }

    // 7. Month Navigation Next Month Regular
    @Test
    fun test07_monthNavigationNextMonthRegular() {
        val (year, month) = MonthlyCalendarEngine.getNextMonth(2026, Calendar.APRIL)
        assertEquals(2026, year)
        assertEquals(Calendar.MAY, month)
    }

    // 8. Month Navigation Year Boundary Forward
    @Test
    fun test08_monthNavigationYearBoundaryForward() {
        val (year, month) = MonthlyCalendarEngine.getNextMonth(2026, Calendar.DECEMBER)
        assertEquals(2027, year)
        assertEquals(Calendar.JANUARY, month)
    }

    // 9. Month Navigation Year Boundary Backward
    @Test
    fun test09_monthNavigationYearBoundaryBackward() {
        val (year, month) = MonthlyCalendarEngine.getPreviousMonth(2026, Calendar.JANUARY)
        assertEquals(2025, year)
        assertEquals(Calendar.DECEMBER, month)
    }

    // 10. Jump to Today Restores Current Month
    @Test
    fun test10_goToTodayRestoresCurrentMonth() = runBlocking {
        val vm = createTransactionsViewModel()
        vm.nextCalendarMonth()
        vm.nextCalendarMonth()
        assertEquals(Calendar.JANUARY, vm.uiState.value.calendarMonth)
        assertEquals(2027, vm.uiState.value.calendarYear)

        vm.goToTodayCalendar()
        assertEquals(Calendar.NOVEMBER, vm.uiState.value.calendarMonth)
        assertEquals(2026, vm.uiState.value.calendarYear)
    }

    // 11. Personal Expense Calculation Inside Daily Cell
    @Test
    fun test11_personalExpenseCalculationInsideDailyCell() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 15)
        val txn = Transaction(
            title = "Groceries",
            amountPaise = 100000L,
            transactionDate = ts,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L
        )
        vm.saveTransaction(txn)

        val state = vm.uiState.first { it.calendarGridData != null && it.calendarGridData!!.totalMonthExpensePaise > 0L }
        val cell15 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 15 }
        assertNotNull(cell15.summary)
        assertEquals(100000L, cell15.summary!!.totalExpensePaise)
    }

    // 12. Split Expense Friend Share Excluded
    @Test
    fun test12_splitExpenseFriendShareExcluded() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 10)
        // Split expense: gross 300000 paise, user share 100000 paise, Alice and Bob each 100000 paise
        val txn = Transaction(
            title = "Team Dinner",
            amountPaise = 300000L,
            transactionDate = ts,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 10L, friendName = "Alice", shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 20L, friendName = "Bob", shareAmountPaise = 100000L)
            )
        )
        vm.saveTransaction(txn)

        val state = vm.uiState.first { it.calendarGridData != null && it.calendarGridData!!.totalMonthExpensePaise > 0L }
        val cell10 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 10 }
        assertNotNull(cell10.summary)
        // User share is exactly 100000 paise
        assertEquals(100000L, cell10.summary!!.totalExpensePaise)
    }

    // 13. Income Calculation Inside Daily Cell
    @Test
    fun test13_incomeCalculationInsideDailyCell() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 5)
        val txn = Transaction(
            title = "Salary",
            amountPaise = 5000000L,
            transactionDate = ts,
            transactionType = TransactionType.INCOME,
            categoryId = 1L
        )
        vm.saveTransaction(txn)

        val state = vm.uiState.first { it.calendarGridData != null && it.calendarGridData!!.totalMonthIncomePaise > 0L }
        val cell5 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 5 }
        assertNotNull(cell5.summary)
        assertEquals(5000000L, cell5.summary!!.totalIncomePaise)
        assertEquals(0L, cell5.summary!!.totalExpensePaise)
    }

    // 14. Refund Calculation Inside Daily Cell
    @Test
    fun test14_refundCalculationInsideDailyCell() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 8)
        val txn = Transaction(
            title = "Item Return",
            amountPaise = 50000L,
            transactionDate = ts,
            transactionType = TransactionType.REFUND,
            categoryId = 1L
        )
        vm.saveTransaction(txn)

        val state = vm.uiState.first { it.calendarGridData != null && it.calendarGridData!!.totalMonthRefundPaise > 0L }
        val cell8 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 8 }
        assertNotNull(cell8.summary)
        assertEquals(50000L, cell8.summary!!.totalRefundPaise)
    }

    // 15. Transfer Transactions Handling
    @Test
    fun test15_transferTransactionsHandling() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 12)
        val txn = Transaction(
            title = "Account Transfer",
            amountPaise = 200000L,
            transactionDate = ts,
            transactionType = TransactionType.TRANSFER,
            categoryId = 1L
        )
        vm.saveTransaction(txn)

        val state = vm.uiState.first { it.calendarGridData != null && it.allTransactions.isNotEmpty() }
        val cell12 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 12 }
        assertNotNull(cell12.summary)
        assertEquals(0L, cell12.summary!!.totalExpensePaise)
        assertEquals(0L, cell12.summary!!.totalIncomePaise)
        assertEquals(200000L, cell12.summary!!.totalTransferPaise)
    }

    // 16. Ignored Transaction Excluded by Default
    @Test
    fun test16_ignoredTransactionExcludedByDefault() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 14)
        val txn = Transaction(
            title = "Ignored Expense",
            amountPaise = 75000L,
            transactionDate = ts,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L,
            transactionStatus = TransactionStatus.IGNORED
        )
        vm.saveTransaction(txn)

        val state = vm.uiState.first { it.allTransactions.isNotEmpty() }
        val cell14 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 14 }
        assertEquals(0L, cell14.summary!!.totalExpensePaise)
    }

    // 17. Ignored Transaction Included When Ignored Filter Active
    @Test
    fun test17_ignoredTransactionIncludedWhenIgnoredFilterActive() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 14)
        val txn = Transaction(
            title = "Ignored Expense",
            amountPaise = 75000L,
            transactionDate = ts,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L,
            transactionStatus = TransactionStatus.IGNORED
        )
        vm.saveTransaction(txn)
        vm.uiState.first { it.allTransactions.isNotEmpty() }
        vm.onTypeFilterSelected("IGNORED")

        val state = vm.uiState.first { it.selectedTypeFilter == "IGNORED" && it.filteredTransactions.isNotEmpty() }
        val cell14 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 14 }
        assertEquals(75000L, cell14.summary!!.totalExpensePaise)
    }

    // 18. Multiple Transactions on Same Date Aggregation
    @Test
    fun test18_multipleTransactionsOnSameDateAggregation() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 18)
        vm.saveTransaction(Transaction(title = "Coffee", amountPaise = 20000L, transactionDate = ts, transactionType = TransactionType.EXPENSE, categoryId = 1L))
        vm.saveTransaction(Transaction(title = "Lunch", amountPaise = 30000L, transactionDate = ts, transactionType = TransactionType.EXPENSE, categoryId = 1L))
        vm.saveTransaction(Transaction(title = "Dinner", amountPaise = 50000L, transactionDate = ts, transactionType = TransactionType.EXPENSE, categoryId = 1L))

        val state = vm.uiState.first { it.calendarGridData != null && it.calendarGridData!!.totalMonthExpensePaise >= 100000L }
        val cell18 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 18 }
        assertEquals(100000L, cell18.summary!!.totalExpensePaise)
        assertEquals(3, cell18.summary!!.transactionCount)
    }

    // 19. No Transaction Date Shows Zero Totals
    @Test
    fun test19_noTransactionDateShowsZeroTotals() {
        val grid = MonthlyCalendarEngine.computeMonthGrid(
            year = 2026,
            month = Calendar.NOVEMBER,
            transactions = emptyList(),
            timeZone = utcTimeZone,
            locale = usLocale
        )
        val cell1 = grid.cells.first { it.isCurrentMonth && it.dayOfMonth == 1 }
        assertNotNull(cell1.summary)
        assertEquals(0L, cell1.summary!!.totalExpensePaise)
        assertEquals(0L, cell1.summary!!.totalIncomePaise)
        assertEquals(0, cell1.summary!!.transactionCount)
        assertFalse(cell1.summary!!.hasTransactions)
    }

    // 20. Future Date in Current Month Has No Fabricated Transactions
    @Test
    fun test20_futureDateInCurrentMonthHasNoTransactions() {
        val grid = MonthlyCalendarEngine.computeMonthGrid(
            year = 2026,
            month = Calendar.NOVEMBER,
            transactions = emptyList(),
            timeZone = utcTimeZone,
            locale = usLocale
        )
        val futureCell = grid.cells.first { it.isCurrentMonth && it.dayOfMonth == 28 }
        assertNotNull(futureCell.summary)
        assertEquals(0, futureCell.summary!!.transactionCount)
        assertEquals(0L, futureCell.summary!!.totalExpensePaise)
    }

    // 21. Compact Currency Formatting Kilo
    @Test
    fun test21_compactCurrencyFormattingKilo() {
        val formatted = CurrencyUtils.formatPaiseCompact(150000L) // ₹1,500
        assertTrue(formatted.contains("1.5k") || formatted.contains("1.50k"))
    }

    // 22. Compact Currency Formatting Lakh
    @Test
    fun test22_compactCurrencyFormattingLakh() {
        val formatted = CurrencyUtils.formatPaiseCompact(15000000L) // ₹1,50,000
        assertTrue(formatted.contains("1.5L"))
    }

    // 23. Compact Currency Formatting Crore
    @Test
    fun test23_compactCurrencyFormattingCrore() {
        val formatted = CurrencyUtils.formatPaiseCompact(1500000000L) // ₹1,50,00,000
        assertTrue(formatted.contains("1.5Cr"))
    }

    // 24. Compact Currency Formatting Small Amount
    @Test
    fun test24_compactCurrencyFormattingSmallAmount() {
        val formatted = CurrencyUtils.formatPaiseCompact(50000L) // ₹500
        assertEquals("₹500", formatted)
    }

    // 25. Date Selection Updates Selected Date Millis
    @Test
    fun test25_dateSelectionUpdatesSelectedDateMillis() {
        val vm = createTransactionsViewModel()
        val targetTs = makeTimestamp(2026, Calendar.NOVEMBER, 22)
        vm.selectCalendarDate(targetTs)
        assertEquals(targetTs, vm.uiState.value.selectedCalendarDateMillis)
    }

    // 26. Date Selection Extracts Selected Day Summary
    @Test
    fun test26_dateSelectionExtractsSelectedDaySummary() = runBlocking {
        val vm = createTransactionsViewModel()
        val targetTs = makeTimestamp(2026, Calendar.NOVEMBER, 17)
        val txn = Transaction(
            title = "Stationery",
            amountPaise = 45000L,
            transactionDate = targetTs,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L
        )
        vm.saveTransaction(txn)
        vm.selectCalendarDate(targetTs)

        val state = vm.uiState.first { it.selectedDaySummary != null && it.selectedDaySummary!!.totalExpensePaise > 0L }
        assertEquals(17, state.selectedDaySummary!!.dayOfMonth)
        assertEquals(45000L, state.selectedDaySummary!!.totalExpensePaise)
        assertEquals(1, state.selectedDaySummary!!.transactionCount)
    }

    // 27. Date Selection Across Month Boundary
    @Test
    fun test27_dateSelectionAcrossMonthBoundary() {
        val vm = createTransactionsViewModel()
        val decTs = makeTimestamp(2026, Calendar.DECEMBER, 25)
        vm.selectCalendarDate(decTs)

        assertEquals(2026, vm.uiState.value.calendarYear)
        assertEquals(Calendar.DECEMBER, vm.uiState.value.calendarMonth)
        assertEquals(decTs, vm.uiState.value.selectedCalendarDateMillis)
    }

    // 28. View Mode Toggle List to Calendar
    @Test
    fun test28_viewModeToggleListToCalendar() {
        val vm = createTransactionsViewModel()
        assertEquals(TransactionsViewMode.LIST, vm.uiState.value.viewMode)

        vm.setViewMode(TransactionsViewMode.CALENDAR)
        assertEquals(TransactionsViewMode.CALENDAR, vm.uiState.value.viewMode)

        vm.setViewMode(TransactionsViewMode.LIST)
        assertEquals(TransactionsViewMode.LIST, vm.uiState.value.viewMode)
    }

    // 29. View Mode Preserves Active Search Query
    @Test
    fun test29_viewModePreservesActiveSearchQuery() {
        val vm = createTransactionsViewModel()
        vm.onSearchQueryChanged("Supermarket")
        vm.setViewMode(TransactionsViewMode.CALENDAR)

        assertEquals("Supermarket", vm.uiState.value.searchQuery)
        assertEquals(TransactionsViewMode.CALENDAR, vm.uiState.value.viewMode)
    }

    // 30. View Mode Preserves Active Type Filter
    @Test
    fun test30_viewModePreservesActiveTypeFilter() {
        val vm = createTransactionsViewModel()
        vm.onTypeFilterSelected("EXPENSE")
        vm.setViewMode(TransactionsViewMode.CALENDAR)

        assertEquals("EXPENSE", vm.uiState.value.selectedTypeFilter)
        assertEquals(TransactionsViewMode.CALENDAR, vm.uiState.value.viewMode)
    }

    // 31. View Mode Preserves Active Payment Method Filter
    @Test
    fun test31_viewModePreservesActivePaymentMethodFilter() {
        val vm = createTransactionsViewModel()
        vm.onPaymentMethodFilterSelected(PaymentMethod.UPI)
        vm.setViewMode(TransactionsViewMode.CALENDAR)

        assertEquals(PaymentMethod.UPI, vm.uiState.value.selectedPaymentMethodFilter)
        assertEquals(TransactionsViewMode.CALENDAR, vm.uiState.value.viewMode)
    }

    // 32. Search Query Filters Calendar Totals
    @Test
    fun test32_searchQueryFiltersCalendarTotals() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 18)
        vm.saveTransaction(Transaction(title = "Coffee", amountPaise = 15000L, transactionDate = ts, transactionType = TransactionType.EXPENSE, categoryId = 1L))
        vm.saveTransaction(Transaction(title = "Laptop", amountPaise = 5000000L, transactionDate = ts, transactionType = TransactionType.EXPENSE, categoryId = 1L))

        vm.uiState.first { it.allTransactions.size >= 2 }
        vm.onSearchQueryChanged("Coffee")
        val state = vm.uiState.first { it.searchQuery == "Coffee" && it.filteredTransactions.size == 1 }
        val cell18 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 18 }
        assertEquals(15000L, cell18.summary!!.totalExpensePaise)
        assertEquals(1, cell18.summary!!.transactionCount)
    }

    // 33. Type Filter Expense Only
    @Test
    fun test33_typeFilterExpenseOnly() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 18)
        vm.saveTransaction(Transaction(title = "Snack", amountPaise = 20000L, transactionDate = ts, transactionType = TransactionType.EXPENSE, categoryId = 1L))
        vm.saveTransaction(Transaction(title = "Bonus", amountPaise = 100000L, transactionDate = ts, transactionType = TransactionType.INCOME, categoryId = 1L))

        vm.uiState.first { it.allTransactions.size >= 2 }
        vm.onTypeFilterSelected("EXPENSE")
        val state = vm.uiState.first { it.selectedTypeFilter == "EXPENSE" && it.filteredTransactions.size == 1 }
        val cell18 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 18 }
        assertEquals(20000L, cell18.summary!!.totalExpensePaise)
        assertEquals(0L, cell18.summary!!.totalIncomePaise)
    }

    // 34. Type Filter Income Only
    @Test
    fun test34_typeFilterIncomeOnly() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 18)
        vm.saveTransaction(Transaction(title = "Snack", amountPaise = 20000L, transactionDate = ts, transactionType = TransactionType.EXPENSE, categoryId = 1L))
        vm.saveTransaction(Transaction(title = "Bonus", amountPaise = 100000L, transactionDate = ts, transactionType = TransactionType.INCOME, categoryId = 1L))

        vm.uiState.first { it.allTransactions.size >= 2 }
        vm.onTypeFilterSelected("INCOME")
        val state = vm.uiState.first { it.selectedTypeFilter == "INCOME" && it.filteredTransactions.size == 1 }
        val cell18 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 18 }
        assertEquals(0L, cell18.summary!!.totalExpensePaise)
        assertEquals(100000L, cell18.summary!!.totalIncomePaise)
    }

    // 35. Type Filter Refund Only
    @Test
    fun test35_typeFilterRefundOnly() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 18)
        vm.saveTransaction(Transaction(title = "Snack", amountPaise = 20000L, transactionDate = ts, transactionType = TransactionType.EXPENSE, categoryId = 1L))
        vm.saveTransaction(Transaction(title = "Refund", amountPaise = 10000L, transactionDate = ts, transactionType = TransactionType.REFUND, categoryId = 1L))

        vm.uiState.first { it.allTransactions.size >= 2 }
        vm.onTypeFilterSelected("REFUND")
        val state = vm.uiState.first { it.selectedTypeFilter == "REFUND" && it.filteredTransactions.size == 1 }
        val cell18 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 18 }
        assertEquals(0L, cell18.summary!!.totalExpensePaise)
        assertEquals(10000L, cell18.summary!!.totalRefundPaise)
    }

    // 36. Fast Add Initial Date Prefills Selected Date
    @Test
    fun test36_fastAddInitialDateMillisPrefillsSelectedDate() {
        val targetTs = makeTimestamp(2026, Calendar.NOVEMBER, 21)
        val newTxn = Transaction(
            title = "Prefilled Transaction",
            amountPaise = 50000L,
            transactionDate = targetTs,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L
        )
        assertEquals(targetTs, newTxn.transactionDate)
    }

    // 37. Fast Add Default Null Uses Current Time
    @Test
    fun test37_fastAddDefaultNullUsesCurrentTime() {
        val initialDateMillis: Long? = null
        val resolvedMillis = initialDateMillis ?: fixedNow
        assertEquals(fixedNow, resolvedMillis)
    }

    // 38. Trip Constituent Transactions Not Double Counted
    @Test
    fun test38_tripConstituentTransactionsNotDoubleCounted() = runBlocking {
        val vm = createTransactionsViewModel()
        database.expenseGroupDao().insertExpenseGroup(ExpenseGroupEntity(id = 100L, name = "Goa Trip"))

        val ts = makeTimestamp(2026, Calendar.NOVEMBER, 16)
        val txn = Transaction(
            title = "Flight Ticket",
            amountPaise = 400000L,
            transactionDate = ts,
            transactionType = TransactionType.EXPENSE,
            categoryId = 2L,
            expenseGroupId = 100L
        )
        vm.saveTransaction(txn)

        val state = vm.uiState.first { it.calendarGridData != null && it.calendarGridData!!.totalMonthExpensePaise > 0L }
        val cell16 = state.calendarGridData!!.cells.first { it.isCurrentMonth && it.dayOfMonth == 16 }
        // Flight ticket is accounted for exactly once on Nov 16
        assertEquals(400000L, cell16.summary!!.totalExpensePaise)
        assertEquals(1, cell16.summary!!.transactionCount)
    }

    // 39. Month Total Expenses Aggregation
    @Test
    fun test39_monthTotalExpensesAggregation() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts1 = makeTimestamp(2026, Calendar.NOVEMBER, 2)
        val ts2 = makeTimestamp(2026, Calendar.NOVEMBER, 12)
        vm.saveTransaction(Transaction(title = "Txn1", amountPaise = 100000L, transactionDate = ts1, transactionType = TransactionType.EXPENSE, categoryId = 1L))
        vm.saveTransaction(Transaction(title = "Txn2", amountPaise = 250000L, transactionDate = ts2, transactionType = TransactionType.EXPENSE, categoryId = 1L))

        val state = vm.uiState.first { it.calendarGridData != null && it.calendarGridData!!.totalMonthExpensePaise >= 350000L }
        assertEquals(350000L, state.calendarGridData!!.totalMonthExpensePaise)
    }

    // 40. Month Total Income Aggregation
    @Test
    fun test40_monthTotalIncomeAggregation() = runBlocking {
        val vm = createTransactionsViewModel()
        val ts1 = makeTimestamp(2026, Calendar.NOVEMBER, 1)
        val ts2 = makeTimestamp(2026, Calendar.NOVEMBER, 15)
        vm.saveTransaction(Transaction(title = "Income1", amountPaise = 3000000L, transactionDate = ts1, transactionType = TransactionType.INCOME, categoryId = 1L))
        vm.saveTransaction(Transaction(title = "Income2", amountPaise = 2000000L, transactionDate = ts2, transactionType = TransactionType.INCOME, categoryId = 1L))

        val state = vm.uiState.first { it.calendarGridData != null && it.calendarGridData!!.totalMonthIncomePaise >= 5000000L }
        assertEquals(5000000L, state.calendarGridData!!.totalMonthIncomePaise)
    }

    // 41. TimeZone Change Consistency
    @Test
    fun test41_timeZoneChangeConsistency() {
        val istTz = TimeZone.getTimeZone("Asia/Kolkata")
        val jstTz = TimeZone.getTimeZone("Asia/Tokyo")

        val novBlanksUtc = MonthlyCalendarEngine.getLeadingBlankCount(2026, Calendar.NOVEMBER, utcTimeZone, usLocale)
        val novBlanksIst = MonthlyCalendarEngine.getLeadingBlankCount(2026, Calendar.NOVEMBER, istTz, usLocale)
        val novBlanksJst = MonthlyCalendarEngine.getLeadingBlankCount(2026, Calendar.NOVEMBER, jstTz, usLocale)

        assertEquals(6, novBlanksUtc)
        assertEquals(6, novBlanksIst)
        assertEquals(6, novBlanksJst)
    }

    // 42. Calendar Cell isSameDay Accuracy
    @Test
    fun test42_calendarCellIsSameDayAccuracy() {
        val t1 = makeTimestamp(2026, Calendar.NOVEMBER, 18, 9, 30)
        val t2 = makeTimestamp(2026, Calendar.NOVEMBER, 18, 21, 45)
        val t3 = makeTimestamp(2026, Calendar.NOVEMBER, 19, 9, 30)

        assertTrue(MonthlyCalendarEngine.isSameDay(t1, t2, utcTimeZone))
        assertFalse(MonthlyCalendarEngine.isSameDay(t1, t3, utcTimeZone))
    }
}
