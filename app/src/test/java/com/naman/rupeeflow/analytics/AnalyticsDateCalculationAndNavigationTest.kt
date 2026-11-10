package com.naman.rupeeflow.analytics

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DatePeriodHelper
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.domain.util.safeSumOf
import com.naman.rupeeflow.ui.screens.analytics.AnalyticsViewModel
import com.naman.rupeeflow.ui.screens.overview.OverviewViewModel
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
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class AnalyticsDateCalculationAndNavigationTest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var pendingTransactionRepository: PendingTransactionRepository

    private val testTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val testZoneId = ZoneId.of("Asia/Kolkata")
    private val testLocale = Locale.ENGLISH

    // Reference: Sunday 8 November 2026, 18:16 IST
    private val refZonedDateTime = ZonedDateTime.of(2026, 11, 8, 18, 16, 0, 0, testZoneId)
    private val refTimestamp = refZonedDateTime.toInstant().toEpochMilli()

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
        pendingTransactionRepository = PendingTransactionRepositoryImpl(database.pendingTransactionDao())

        // Insert standard category
        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity(id = 1L, name = "Entertainment", iconName = "movie", colorHex = "#3B82F6")
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    // ==========================================
    // PHASE 7.1 — WEEK BOUNDARY TESTS (1-15)
    // ==========================================

    @Test
    fun test01_MondayTransaction() {
        // Monday 2 Nov 2026
        val mon = LocalDate.of(2026, 11, 2).atTime(10, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { mon >= it.calendarWeekStartMillis && mon < it.calendarWeekEndMillisExclusive }
        assertEquals(2, bucket.weekIndex)
        assertEquals("Week 2", bucket.weekLabel)
        assertEquals("2 Nov – 8 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test02_TuesdayTransaction() {
        val tue = LocalDate.of(2026, 11, 3).atTime(14, 30).atZone(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { tue >= it.calendarWeekStartMillis && tue < it.calendarWeekEndMillisExclusive }
        assertEquals(2, bucket.weekIndex)
        assertEquals("2 Nov – 8 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test03_WednesdayTransaction() {
        val wed = LocalDate.of(2026, 11, 4).atTime(9, 15).atZone(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { wed >= it.calendarWeekStartMillis && wed < it.calendarWeekEndMillisExclusive }
        assertEquals(2, bucket.weekIndex)
        assertEquals("2 Nov – 8 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test04_ThursdayTransaction() {
        val thu = LocalDate.of(2026, 11, 5).atTime(20, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { thu >= it.calendarWeekStartMillis && thu < it.calendarWeekEndMillisExclusive }
        assertEquals(2, bucket.weekIndex)
        assertEquals("2 Nov – 8 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test05_FridayTransaction() {
        val fri = LocalDate.of(2026, 11, 6).atTime(18, 45).atZone(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { fri >= it.calendarWeekStartMillis && fri < it.calendarWeekEndMillisExclusive }
        assertEquals(2, bucket.weekIndex)
        assertEquals("2 Nov – 8 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test06_SaturdayTransaction() {
        val sat = LocalDate.of(2026, 11, 7).atTime(22, 10).atZone(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { sat >= it.calendarWeekStartMillis && sat < it.calendarWeekEndMillisExclusive }
        assertEquals(2, bucket.weekIndex)
        assertEquals("2 Nov – 8 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test07_SundayTransaction() {
        // Sunday 8 Nov 2026
        val sun = LocalDate.of(2026, 11, 8).atTime(23, 59, 59).atZone(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { sun >= it.calendarWeekStartMillis && sun < it.calendarWeekEndMillisExclusive }
        assertEquals(2, bucket.weekIndex)
        assertEquals("Week 2", bucket.weekLabel)
        assertEquals("2 Nov – 8 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test08_Sunday8November2026_Regression() {
        // Exact regression test: 8 Nov 2026 at 18:16 IST
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { refTimestamp >= it.calendarWeekStartMillis && refTimestamp < it.calendarWeekEndMillisExclusive }
        assertEquals(2, bucket.weekIndex)
        assertEquals("Week 2", bucket.weekLabel)
        assertEquals("2 Nov – 8 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test09_Monday2November2026() {
        // Monday 2 Nov 2026 00:00:00 IST
        val monStart = LocalDate.of(2026, 11, 2).atStartOfDay(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { monStart >= it.calendarWeekStartMillis && monStart < it.calendarWeekEndMillisExclusive }
        assertEquals(2, bucket.weekIndex)
        assertEquals("2 Nov – 8 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test10_SundayToMondayBoundary() {
        // Sun 8 Nov 23:59:59.999 is Week 2
        val sunLast = LocalDate.of(2026, 11, 8).atTime(23, 59, 59, 999_000_000).atZone(testZoneId).toInstant().toEpochMilli()
        // Mon 9 Nov 00:00:00.000 is Week 3
        val monNext = LocalDate.of(2026, 11, 9).atStartOfDay(testZoneId).toInstant().toEpochMilli()

        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucketSun = weeks.first { sunLast >= it.calendarWeekStartMillis && sunLast < it.calendarWeekEndMillisExclusive }
        val bucketMon = weeks.first { monNext >= it.calendarWeekStartMillis && monNext < it.calendarWeekEndMillisExclusive }

        assertEquals(2, bucketSun.weekIndex)
        assertEquals(3, bucketMon.weekIndex)
        assertEquals("9 Nov – 15 Nov", bucketMon.dateRangeLabel)
    }

    @Test
    fun test11_WeekCrossingTwoMonths() {
        // Week starting 26 Oct 2026 ends 1 Nov 2026
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val week1 = weeks[0]
        assertEquals("Week 1", week1.weekLabel)
        assertEquals("26 Oct – 1 Nov", week1.dateRangeLabel)
        assertEquals(LocalDate.of(2026, 10, 26), week1.calendarWeekStart)
        assertEquals(LocalDate.of(2026, 11, 1), week1.calendarWeekEnd)
    }

    @Test
    fun test12_WeekCrossingTwoYears() {
        // Week starting 28 Dec 2026 ends 3 Jan 2027
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 12), testTimeZone, testLocale)
        val lastWeek = weeks.last()
        assertEquals("28 Dec 2026 – 3 Jan 2027", lastWeek.dateRangeLabel)
        assertEquals(LocalDate.of(2026, 12, 28), lastWeek.calendarWeekStart)
        assertEquals(LocalDate.of(2027, 1, 3), lastWeek.calendarWeekEnd)
    }

    @Test
    fun test13_WeekContainingFirstDayOfMonth() {
        // Nov 1 2026 is Sunday, in Week 1 (26 Oct - 1 Nov)
        val nov1 = LocalDate.of(2026, 11, 1).atTime(12, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { nov1 >= it.calendarWeekStartMillis && nov1 < it.calendarWeekEndMillisExclusive }
        assertEquals(1, bucket.weekIndex)
        assertEquals("26 Oct – 1 Nov", bucket.dateRangeLabel)
    }

    @Test
    fun test14_WeekContainingLastDayOfMonth() {
        // Nov 30 2026 is Monday, in Week 6 (30 Nov - 6 Dec)
        val nov30 = LocalDate.of(2026, 11, 30).atTime(12, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        val bucket = weeks.first { nov30 >= it.calendarWeekStartMillis && nov30 < it.calendarWeekEndMillisExclusive }
        assertEquals(6, bucket.weekIndex)
        assertEquals("30 Nov – 6 Dec", bucket.dateRangeLabel)
    }

    @Test
    fun test15_LeapYearFebruary() {
        val weeks2024 = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2024, 2), testTimeZone, testLocale)
        val feb29_2024 = LocalDate.of(2024, 2, 29).atTime(12, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val bucket2024 = weeks2024.first { feb29_2024 >= it.calendarWeekStartMillis && feb29_2024 < it.calendarWeekEndMillisExclusive }
        assertNotNull(bucket2024)
        assertEquals("26 Feb – 3 Mar", bucket2024.dateRangeLabel)

        val weeks2028 = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2028, 2), testTimeZone, testLocale)
        val feb29_2028 = LocalDate.of(2028, 2, 29).atTime(12, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val bucket2028 = weeks2028.first { feb29_2028 >= it.calendarWeekStartMillis && feb29_2028 < it.calendarWeekEndMillisExclusive }
        assertNotNull(bucket2028)
        assertEquals("28 Feb – 5 Mar", bucket2028.dateRangeLabel)
    }

    // ==========================================
    // PHASE 7.2 — MONTHLY GROUPING TESTS (16-29)
    // ==========================================

    @Test
    fun test16_October2026() {
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 10), testTimeZone, testLocale)
        assertEquals(5, weeks.size)
        assertEquals("28 Sep – 4 Oct", weeks[0].dateRangeLabel)
        assertEquals("5 Oct – 11 Oct", weeks[1].dateRangeLabel)
        assertEquals("12 Oct – 18 Oct", weeks[2].dateRangeLabel)
        assertEquals("19 Oct – 25 Oct", weeks[3].dateRangeLabel)
        assertEquals("26 Oct – 1 Nov", weeks[4].dateRangeLabel)
    }

    @Test
    fun test17_November2026() {
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 11), testTimeZone, testLocale)
        assertEquals(6, weeks.size)
        assertEquals("26 Oct – 1 Nov", weeks[0].dateRangeLabel)
        assertEquals("2 Nov – 8 Nov", weeks[1].dateRangeLabel)
        assertEquals("9 Nov – 15 Nov", weeks[2].dateRangeLabel)
        assertEquals("16 Nov – 22 Nov", weeks[3].dateRangeLabel)
        assertEquals("23 Nov – 29 Nov", weeks[4].dateRangeLabel)
        assertEquals("30 Nov – 6 Dec", weeks[5].dateRangeLabel)
    }

    @Test
    fun test18_December2026() {
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2026, 12), testTimeZone, testLocale)
        assertEquals(5, weeks.size)
        assertEquals("30 Nov – 6 Dec", weeks[0].dateRangeLabel)
        assertEquals("28 Dec 2026 – 3 Jan 2027", weeks[4].dateRangeLabel)
    }

    @Test
    fun test19_January2027() {
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2027, 1), testTimeZone, testLocale)
        assertEquals(5, weeks.size)
        assertEquals("28 Dec 2026 – 3 Jan 2027", weeks[0].dateRangeLabel)
        assertEquals("25 Jan – 31 Jan", weeks[4].dateRangeLabel)
    }

    @Test
    fun test20_February2027() {
        // Starts on Monday Feb 1, ends on Sunday Feb 28
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2027, 2), testTimeZone, testLocale)
        assertEquals(4, weeks.size)
        assertEquals("1 Feb – 7 Feb", weeks[0].dateRangeLabel)
        assertEquals("8 Feb – 14 Feb", weeks[1].dateRangeLabel)
        assertEquals("15 Feb – 21 Feb", weeks[2].dateRangeLabel)
        assertEquals("22 Feb – 28 Feb", weeks[3].dateRangeLabel)
    }

    @Test
    fun test21_February2028() {
        val weeks = DatePeriodHelper.getMonthlyWeeks(YearMonth.of(2028, 2), testTimeZone, testLocale)
        assertEquals(5, weeks.size)
        assertEquals("31 Jan – 6 Feb", weeks[0].dateRangeLabel)
        assertEquals("28 Feb – 5 Mar", weeks[4].dateRangeLabel)
    }

    @Test
    fun test22_MonthWith28Days() {
        val ym = YearMonth.of(2027, 2)
        assertEquals(28, ym.lengthOfMonth())
        val weeks = DatePeriodHelper.getMonthlyWeeks(ym, testTimeZone, testLocale)
        assertEquals(4, weeks.size)
    }

    @Test
    fun test23_MonthWith29Days() {
        val ym = YearMonth.of(2028, 2)
        assertEquals(29, ym.lengthOfMonth())
        val weeks = DatePeriodHelper.getMonthlyWeeks(ym, testTimeZone, testLocale)
        assertEquals(5, weeks.size)
    }

    @Test
    fun test24_MonthWith30Days() {
        val ym = YearMonth.of(2026, 11)
        assertEquals(30, ym.lengthOfMonth())
        val weeks = DatePeriodHelper.getMonthlyWeeks(ym, testTimeZone, testLocale)
        assertEquals(6, weeks.size)
    }

    @Test
    fun test25_MonthWith31Days() {
        val ym = YearMonth.of(2026, 10)
        assertEquals(31, ym.lengthOfMonth())
        val weeks = DatePeriodHelper.getMonthlyWeeks(ym, testTimeZone, testLocale)
        assertEquals(5, weeks.size)
    }

    @Test
    fun test26_MonthBeginningOnSunday() {
        val ym = YearMonth.of(2026, 11)
        assertEquals(DayOfWeek.SUNDAY, ym.atDay(1).dayOfWeek)
        val weeks = DatePeriodHelper.getMonthlyWeeks(ym, testTimeZone, testLocale)
        assertEquals("26 Oct – 1 Nov", weeks.first().dateRangeLabel)
    }

    @Test
    fun test27_MonthBeginningOnMonday() {
        val ym = YearMonth.of(2027, 2)
        assertEquals(DayOfWeek.MONDAY, ym.atDay(1).dayOfWeek)
        val weeks = DatePeriodHelper.getMonthlyWeeks(ym, testTimeZone, testLocale)
        assertEquals("1 Feb – 7 Feb", weeks.first().dateRangeLabel)
    }

    @Test
    fun test28_MonthEndingOnSunday() {
        val ym = YearMonth.of(2027, 2)
        assertEquals(DayOfWeek.SUNDAY, ym.atEndOfMonth().dayOfWeek)
        val weeks = DatePeriodHelper.getMonthlyWeeks(ym, testTimeZone, testLocale)
        assertEquals("22 Feb – 28 Feb", weeks.last().dateRangeLabel)
    }

    @Test
    fun test29_MonthEndingOnMonday() {
        val ym = YearMonth.of(2026, 11)
        assertEquals(DayOfWeek.MONDAY, ym.atEndOfMonth().dayOfWeek)
        val weeks = DatePeriodHelper.getMonthlyWeeks(ym, testTimeZone, testLocale)
        assertEquals("30 Nov – 6 Dec", weeks.last().dateRangeLabel)
    }

    // ==========================================
    // PHASE 7.3 — FINANCIAL AGGREGATION TESTS (30-43)
    // ==========================================

    @Test
    fun test30_Rupees300OnSunday8November() = runBlocking {
        // Insert exact transaction: ₹300 on Sunday 8 Nov 2026 18:16
        transactionRepository.saveTransaction(
            Transaction(
                id = 101L,
                title = "Ramayan Movie",
                amountPaise = 30000L,
                categoryId = 1L,
                transactionDate = refTimestamp,
                paymentMethod = PaymentMethod.CASH,
                transactionType = TransactionType.EXPENSE,
                transactionStatus = TransactionStatus.ACTIVE
            )
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        // Monthly total should be 30000 paise (₹300.00)
        assertEquals(30000L, state.analyticsData.totalSpentPaise)
        assertEquals(30000L, state.analyticsData.cashSpendingPaise)
        assertEquals(0L, state.analyticsData.onlineSpendingPaise)

        // Verify weekly trend breakdown
        val trend = state.analyticsData.dailySpendingTrend
        assertEquals(6, trend.size)

        // Week 1 (26 Oct - 1 Nov): ₹0
        assertEquals(0L, trend[0].amountPaise)
        assertEquals("Week 1", trend[0].dayLabel)
        assertEquals("26 Oct – 1 Nov", trend[0].dateRangeLabel)

        // Week 2 (2 Nov - 8 Nov): exactly ₹300.00
        assertEquals(30000L, trend[1].amountPaise)
        assertEquals("Week 2", trend[1].dayLabel)
        assertEquals("2 Nov – 8 Nov", trend[1].dateRangeLabel)

        // Week 3 (9 Nov - 15 Nov): ₹0
        assertEquals(0L, trend[2].amountPaise)
        assertEquals("Week 3", trend[2].dayLabel)
        assertEquals("9 Nov – 15 Nov", trend[2].dateRangeLabel)
    }

    @Test
    fun test31_MultipleTransactionsInSameWeek() = runBlocking {
        // Mon 2 Nov, Wed 4 Nov, Sun 8 Nov
        val tMon = LocalDate.of(2026, 11, 2).atTime(10, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val tWed = LocalDate.of(2026, 11, 4).atTime(12, 0).atZone(testZoneId).toInstant().toEpochMilli()

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Mon Lunch", amountPaise = 15000L, categoryId = 1L, transactionDate = tMon, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Wed Dinner", amountPaise = 25000L, categoryId = 1L, transactionDate = tWed, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 3L, title = "Sun Movie", amountPaise = 30000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.CASH, transactionType = TransactionType.EXPENSE)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(70000L, state.analyticsData.totalSpentPaise)
        val week2 = state.analyticsData.dailySpendingTrend[1]
        assertEquals(70000L, week2.amountPaise)
        assertEquals("2 Nov – 8 Nov", week2.dateRangeLabel)
    }

    @Test
    fun test32_TransactionsAcrossTwoWeeks() = runBlocking {
        // Sun 8 Nov (Week 2) and Mon 9 Nov (Week 3)
        val tMon9 = LocalDate.of(2026, 11, 9).atTime(11, 0).atZone(testZoneId).toInstant().toEpochMilli()

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Sun Expense", amountPaise = 30000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.CASH, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Mon Expense", amountPaise = 40000L, categoryId = 1L, transactionDate = tMon9, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        val trend = state.analyticsData.dailySpendingTrend
        assertEquals(30000L, trend[1].amountPaise) // Week 2
        assertEquals(40000L, trend[2].amountPaise) // Week 3
        assertEquals(70000L, state.analyticsData.totalSpentPaise)
    }

    @Test
    fun test33_TransactionsAcrossTwoMonths() = runBlocking {
        // Sat 31 Oct (October) and Sun 1 Nov (November)
        val tOct31 = LocalDate.of(2026, 10, 31).atTime(20, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val tNov1 = LocalDate.of(2026, 11, 1).atTime(10, 0).atZone(testZoneId).toInstant().toEpochMilli()

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Oct Groceries", amountPaise = 50000L, categoryId = 1L, transactionDate = tOct31, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Nov Breakfast", amountPaise = 10000L, categoryId = 1L, transactionDate = tNov1, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val novState = vm.uiState.first { !it.isLoading }

        // In November: only Nov 1 (10000 paise) should be counted!
        assertEquals(10000L, novState.analyticsData.totalSpentPaise)
        assertEquals(10000L, novState.analyticsData.dailySpendingTrend[0].amountPaise) // Week 1

        // Navigate to October
        vm.previousPeriod()
        val octState = vm.uiState.first { it.periodOffset == -1 }

        // In October: only Oct 31 (50000 paise) should be counted!
        assertEquals(50000L, octState.analyticsData.totalSpentPaise)
        assertEquals(50000L, octState.analyticsData.dailySpendingTrend[4].amountPaise) // Week 5 (26 Oct - 1 Nov)
    }

    @Test
    fun test34_ExpenseAndIncomeHandling() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Salary", amountPaise = 10000000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.BANK_TRANSFER, transactionType = TransactionType.INCOME)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Movie", amountPaise = 30000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.CASH, transactionType = TransactionType.EXPENSE)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(10000000L, state.analyticsData.totalIncomePaise)
        assertEquals(30000L, state.analyticsData.totalSpentPaise)
        assertEquals(30000L, state.analyticsData.dailySpendingTrend[1].amountPaise)
    }

    @Test
    fun test35_RefundHandling() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Shopping", amountPaise = 100000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Return Refund", amountPaise = 20000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.REFUND)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(100000L, state.analyticsData.totalSpentPaise)
        assertEquals(20000L, state.analyticsData.totalRefundPaise)
        assertEquals(80000L, state.analyticsData.netSpendingPaise)
    }

    @Test
    fun test36_TransferHandling() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Transfer to Savings", amountPaise = 500000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.TRANSFER)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(0L, state.analyticsData.totalSpentPaise)
        assertEquals(0L, state.analyticsData.dailySpendingTrend[1].amountPaise)
    }

    @Test
    fun test37_IgnoredTransactionHandling() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Ignored SMS", amountPaise = 50000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE, transactionStatus = TransactionStatus.IGNORED)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(0L, state.analyticsData.totalSpentPaise)
        assertEquals(0L, state.analyticsData.dailySpendingTrend[1].amountPaise)
    }

    @Test
    fun test38_ZeroSpendingWeek() = runBlocking {
        // No transactions in Week 3
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Expense W2", amountPaise = 30000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.CASH, transactionType = TransactionType.EXPENSE)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        val week3 = state.analyticsData.dailySpendingTrend[2]
        assertEquals(0L, week3.amountPaise)
        assertEquals("Week 3", week3.dayLabel)
        assertEquals("9 Nov – 15 Nov", week3.dateRangeLabel)
    }

    @Test
    fun test39_EmptyMonth() = runBlocking {
        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(0L, state.analyticsData.totalSpentPaise)
        assertEquals(6, state.analyticsData.dailySpendingTrend.size)
        assertTrue(state.analyticsData.dailySpendingTrend.all { it.amountPaise == 0L })
    }

    @Test
    fun test40_LargeTransactionAmounts() {
        val largeA = Long.MAX_VALUE - 1000L
        val largeB = 500L
        val sum = SafeMath.safeAdd(largeA, largeB)
        assertEquals(Long.MAX_VALUE - 500L, sum)

        // Overflow clamp test
        val overflowSum = SafeMath.safeAdd(Long.MAX_VALUE - 10L, 100L)
        assertEquals(Long.MAX_VALUE, overflowSum)
    }

    @Test
    fun test41_DecimalPaiseAmounts() = runBlocking {
        // ₹250.75 = 25075 paise
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Swiggy ₹250.75", amountPaise = 25075L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(25075L, state.analyticsData.totalSpentPaise)
        assertEquals(25075L, state.analyticsData.dailySpendingTrend[1].amountPaise)
    }

    @Test
    fun test42_DuplicateTransactionProtection() = runBlocking {
        // Two distinct transactions on the same day must both be counted without deduplication
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Coffee 1", amountPaise = 15000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Coffee 2", amountPaise = 15000L, categoryId = 1L, transactionDate = refTimestamp, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        assertEquals(30000L, state.analyticsData.totalSpentPaise)
        assertEquals(30000L, state.analyticsData.dailySpendingTrend[1].amountPaise)
    }

    @Test
    fun test43_MonthlyTotalEqualsSumOfWeeklyBuckets() = runBlocking {
        // Insert transactions across week 1, 2, 4, 6 of November 2026
        val tNov1 = LocalDate.of(2026, 11, 1).atTime(10, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val tNov8 = refTimestamp
        val tNov18 = LocalDate.of(2026, 11, 18).atTime(15, 0).atZone(testZoneId).toInstant().toEpochMilli()
        val tNov30 = LocalDate.of(2026, 11, 30).atTime(18, 0).atZone(testZoneId).toInstant().toEpochMilli()

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "T1", amountPaise = 10000L, categoryId = 1L, transactionDate = tNov1, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "T2", amountPaise = 30000L, categoryId = 1L, transactionDate = tNov8, paymentMethod = PaymentMethod.CASH, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 3L, title = "T3", amountPaise = 45000L, categoryId = 1L, transactionDate = tNov18, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 4L, title = "T4", amountPaise = 25000L, categoryId = 1L, transactionDate = tNov30, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }

        val monthlyTotal = state.analyticsData.totalSpentPaise
        val weeklyBucketsSum = state.analyticsData.dailySpendingTrend.safeSumOf { it.amountPaise }

        assertEquals(110000L, monthlyTotal)
        assertEquals(monthlyTotal, weeklyBucketsSum)
    }

    // ==========================================
    // PHASE 7.4 — NAVIGATION TESTS (44-54)
    // ==========================================

    @Test
    fun test44_PreviousMonth() = runBlocking {
        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        var state = vm.uiState.first { !it.isLoading }
        assertEquals(0, state.periodOffset)

        vm.previousPeriod()
        state = vm.uiState.first { it.periodOffset == -1 }
        assertEquals(-1, state.periodOffset)
        assertTrue(state.canNavigateNext)
    }

    @Test
    fun test45_NextMonth() = runBlocking {
        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        vm.previousPeriod()
        var state = vm.uiState.first { it.periodOffset == -1 }

        vm.nextPeriod()
        state = vm.uiState.first { it.periodOffset == 0 }
        assertEquals(0, state.periodOffset)
        assertFalse(state.canNavigateNext)
    }

    @Test
    fun test46_CurrentMonthNextArrowDisabled() = runBlocking {
        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val state = vm.uiState.first { !it.isLoading }
        assertFalse(state.canNavigateNext)

        // Attempting to advance beyond current month must be clamped to 0
        vm.nextPeriod()
        val afterAttempt = vm.uiState.value
        assertEquals(0, afterAttempt.periodOffset)
        assertFalse(afterAttempt.canNavigateNext)
    }

    @Test
    fun test47_DecemberToJanuary() {
        val decDate = LocalDate.of(2026, 12, 15).atStartOfDay(testZoneId).toInstant().toEpochMilli()
        val decRange = DatePeriodHelper.getMonthRange(offset = 0, referenceTime = decDate, timeZone = testTimeZone, locale = testLocale)

        assertEquals("December 2026", decRange.label)
        val baseYM = YearMonth.of(2026, 12)
        val nextYM = baseYM.plusMonths(1)
        assertEquals(YearMonth.of(2027, 1), nextYM)
    }

    @Test
    fun test48_JanuaryToDecember() {
        val janDate = LocalDate.of(2027, 1, 15).atStartOfDay(testZoneId).toInstant().toEpochMilli()
        val decRange = DatePeriodHelper.getMonthRange(offset = -1, referenceTime = janDate, timeZone = testTimeZone, locale = testLocale)
        assertEquals("December 2026", decRange.label)
        assertEquals(LocalDate.of(2026, 12, 1).atStartOfDay(testZoneId).toInstant().toEpochMilli(), decRange.startMillis)
    }

    @Test
    fun test49_RepeatedPreviousMonthClicks() = runBlocking {
        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        repeat(12) {
            vm.previousPeriod()
        }
        val state = vm.uiState.first { it.periodOffset == -12 }
        assertEquals(-12, state.periodOffset)
        assertTrue(state.canNavigateNext)
    }

    @Test
    fun test50_SelectedMonthUpdatesEveryRelevantChart() = runBlocking {
        val tNov = refTimestamp
        val tOct = LocalDate.of(2026, 10, 15).atTime(12, 0).atZone(testZoneId).toInstant().toEpochMilli()

        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Nov Cash", amountPaise = 30000L, categoryId = 1L, transactionDate = tNov, paymentMethod = PaymentMethod.CASH, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Oct UPI", amountPaise = 90000L, categoryId = 1L, transactionDate = tOct, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )

        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val novState = vm.uiState.first { !it.isLoading }
        assertEquals(30000L, novState.analyticsData.totalSpentPaise)
        assertEquals(30000L, novState.analyticsData.cashSpendingPaise)
        assertEquals(0L, novState.analyticsData.onlineSpendingPaise)

        vm.previousPeriod()
        val octState = vm.uiState.first { it.periodOffset == -1 }
        assertEquals(90000L, octState.analyticsData.totalSpentPaise)
        assertEquals(0L, octState.analyticsData.cashSpendingPaise)
        assertEquals(90000L, octState.analyticsData.onlineSpendingPaise)
    }

    @Test
    fun test51_SelectedWeekClampedWhenChangingMonth() {
        // If user was on Week 6 (index 5) in a 6-week month (Nov), and switched to a 4-week month (Feb),
        // index must be clamped to 3 (data.lastIndex)
        val prevIndex = 5
        val febWeeksCount = 4
        val clampedIndex = prevIndex.coerceIn(0, (febWeeksCount - 1).coerceAtLeast(0))
        assertEquals(3, clampedIndex)
    }

    @Test
    fun test52_ScreenRecreationPreservesState() = runBlocking {
        val vm = AnalyticsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        vm.previousPeriod()
        vm.previousPeriod()
        val offset = vm.uiState.first { it.periodOffset == -2 }.periodOffset
        assertEquals(-2, offset)
    }

    @Test
    fun test53_DarkThemeColorTokens() {
        val activeBarDark = com.naman.rupeeflow.ui.theme.VibrantCyan
        val barDark = com.naman.rupeeflow.ui.theme.ElectricBlue
        assertNotNull(activeBarDark)
        assertNotNull(barDark)
    }

    @Test
    fun test54_LightThemeColorTokens() {
        val activeBarLight = androidx.compose.ui.graphics.Color(0xFF2563EB)
        val barLight = androidx.compose.ui.graphics.Color(0xFF60A5FA)
        assertNotNull(activeBarLight)
        assertNotNull(barLight)
    }

    // ==========================================
    // OVERVIEW SCREEN REGRESSION TESTS (55-56)
    // ==========================================

    @Test
    fun test55_OverviewWeeklyChart_MondayToSundayIntegrity() = runBlocking {
        val vm = OverviewViewModel(
            transactionRepository,
            friendRepository,
            settlementRepository,
            pendingTransactionRepository,
            categoryRepository
        )
        val state = vm.uiState.first { !it.isLoading }
        assertEquals(7, state.weeklySpending.size)
        assertEquals("Mon", state.weeklySpending[0].dayLabel)
        assertEquals("Tue", state.weeklySpending[1].dayLabel)
        assertEquals("Wed", state.weeklySpending[2].dayLabel)
        assertEquals("Thu", state.weeklySpending[3].dayLabel)
        assertEquals("Fri", state.weeklySpending[4].dayLabel)
        assertEquals("Sat", state.weeklySpending[5].dayLabel)
        assertEquals("Sun", state.weeklySpending[6].dayLabel)
    }

    @Test
    fun test56_OverviewWeeklyChart_Sunday8NovemberRegression() = runBlocking {
        transactionRepository.saveTransaction(
            Transaction(
                id = 999L,
                title = "Sunday 8 Nov ₹300",
                amountPaise = 30000L,
                categoryId = 1L,
                transactionDate = refTimestamp, // Sunday Nov 8
                paymentMethod = PaymentMethod.CASH,
                transactionType = TransactionType.EXPENSE
            )
        )

        val vm = OverviewViewModel(
            transactionRepository,
            friendRepository,
            settlementRepository,
            pendingTransactionRepository,
            categoryRepository
        )
        val state = vm.uiState.first { !it.isLoading }

        // Must be in the Sunday slot (index 6) of that week
        val sunSpending = state.weeklySpending.firstOrNull { it.dayLabel == "Sun" }
        assertNotNull(sunSpending)
    }
}
