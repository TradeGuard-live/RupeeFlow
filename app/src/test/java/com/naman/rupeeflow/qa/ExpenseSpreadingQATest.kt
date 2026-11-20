package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.local.entity.TransactionEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.ExpenseAllocationCalculator
import com.naman.rupeeflow.ui.screens.calendar.MonthlyCalendarEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * Complete QA Verification Test Suite for RupeeFlow Expense Spreading / Daily Expense Allocation.
 *
 * Verifies all 20 required scenarios:
 * 1. ₹60 over 5 days = ₹12 per day.
 * 2. ₹100 over 3 days = ₹33.34, ₹33.33, ₹33.33.
 * 3. ₹1 (100 paise) over 3 days = 34p, 33p, 33p; and 1 paisa over 3 days = 1p, 0p, 0p.
 * 4. Allocation sum strictly equals original amount in paise across various amounts.
 * 5. Single-day expense (duration = 1 or spreading disabled).
 * 6. Expense crossing month boundaries (allocations split correctly across months).
 * 7. Expense crossing year boundaries (allocations split correctly across years).
 * 8. Editing amount and duration (recalculates allocations atomically).
 * 9. Changing spread expense back to normal (cleans up allocations, returns to single day).
 * 10. Deleting a spread expense (cascades and deletes allocations).
 * 11. Backup and restore (exports allocations, restores correctly).
 * 12. Daily analytics (uses daily allocation amount).
 * 13. Weekly analytics (uses weekly allocation amounts).
 * 14. Monthly analytics (allocations inside month).
 * 15. Category analytics (allocated amounts attributed to category in period).
 * 16. Budget calculations (allocated amounts attributed to budget period).
 * 17. Calendar summaries (day-wise allocation in MonthlyCalendarEngine).
 * 18. Shared expenses and Split Pro ledger regression (ledger debts/balances remain intact).
 * 19. Old database migration (MIGRATION_14_15 preserves existing data).
 * 20. Old backup compatibility (older backup without spreading restores cleanly).
 */
@RunWith(RobolectricTestRunner::class)
class ExpenseSpreadingQATest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var backupRestoreRepository: BackupRestoreRepository

    private val defaultTz: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val merchantPreferenceRepository = MerchantPreferenceRepositoryImpl(
            inputUsageDao = database.inputUsageDao()
        )

        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao(),
            context = context,
            merchantPreferenceRepository = merchantPreferenceRepository
        )

        categoryRepository = CategoryRepositoryImpl(database.categoryDao())
        friendRepository = FriendRepositoryImpl(database.friendDao())
        settlementRepository = SettlementRepositoryImpl(database.settlementDao(), database.friendDao())

        backupRestoreRepository = BackupRestoreRepository(
            database = database,
            transactionRepository = transactionRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            categoryRepository = categoryRepository,
            context = context
        )

        // Seed default categories
        runBlocking {
            categoryRepository.addCategory(Category(1L, "Food", "restaurant", "#F59E0B", true))
            categoryRepository.addCategory(Category(2L, "Transport", "directions_car", "#3B82F6", true))
            categoryRepository.addCategory(Category(3L, "Utilities", "bolt", "#10B981", true))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun makeCalendarTimestamp(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        return Calendar.getInstance(defaultTz).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // ------------------------------------------------------------------------
    // Scenario 1: ₹60 over 5 days = ₹12 per day
    // ------------------------------------------------------------------------
    @Test
    fun test01_SixtyRupeesOverFiveDays_AllocatesTwelveRupeesPerDay() {
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19)
        val allocations = ExpenseAllocationCalculator.calculateAllocations(
            amountPaise = 6000L, // ₹60.00
            durationDays = 5,
            startDateMillis = startDate,
            timeZone = defaultTz
        )

        assertEquals("Should create exactly 5 daily allocations", 5, allocations.size)
        allocations.forEach { alloc ->
            assertEquals("Each day should be exactly 1200 paise (₹12.00)", 1200L, alloc.amountPaise)
        }
        val sum = allocations.sumOf { it.amountPaise }
        assertEquals(6000L, sum)
    }

    // ------------------------------------------------------------------------
    // Scenario 2: ₹100 over 3 days = ₹33.34, ₹33.33, ₹33.33
    // ------------------------------------------------------------------------
    @Test
    fun test02_OneHundredRupeesOverThreeDays_AllocatesWithDeterministicRemainderPaise() {
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19)
        val allocations = ExpenseAllocationCalculator.calculateAllocations(
            amountPaise = 10000L, // ₹100.00
            durationDays = 3,
            startDateMillis = startDate,
            timeZone = defaultTz
        )

        assertEquals(3, allocations.size)
        // 10000 / 3 = 3333 with remainder 1 paisa -> Day 1 gets 3334
        assertEquals("Day 1 must be 3334 paise (₹33.34)", 3334L, allocations[0].amountPaise)
        assertEquals("Day 2 must be 3333 paise (₹33.33)", 3333L, allocations[1].amountPaise)
        assertEquals("Day 3 must be 3333 paise (₹33.33)", 3333L, allocations[2].amountPaise)
        assertEquals(10000L, allocations.sumOf { it.amountPaise })
    }

    // ------------------------------------------------------------------------
    // Scenario 3: ₹1 (100 paise) over 3 days = 34p, 33p, 33p; and 1 paisa over 3 days = 1p, 0p, 0p
    // ------------------------------------------------------------------------
    @Test
    fun test03_OneRupeeAndOnePaisaAllocations_DistributeRemainderDeterministic() {
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19)

        // ₹1 (100 paise) over 3 days: 100 / 3 = 33 remainder 1 -> 34p, 33p, 33p
        val allocs100 = ExpenseAllocationCalculator.calculateAllocations(
            amountPaise = 100L,
            durationDays = 3,
            startDateMillis = startDate,
            timeZone = defaultTz
        )
        assertEquals(3, allocs100.size)
        assertEquals(34L, allocs100[0].amountPaise)
        assertEquals(33L, allocs100[1].amountPaise)
        assertEquals(33L, allocs100[2].amountPaise)
        assertEquals(100L, allocs100.sumOf { it.amountPaise })

        // 1 paisa over 3 days: 1 / 3 = 0 remainder 1 -> 1p, 0p, 0p
        val allocs1 = ExpenseAllocationCalculator.calculateAllocations(
            amountPaise = 1L,
            durationDays = 3,
            startDateMillis = startDate,
            timeZone = defaultTz
        )
        assertEquals(3, allocs1.size)
        assertEquals(1L, allocs1[0].amountPaise)
        assertEquals(0L, allocs1[1].amountPaise)
        assertEquals(0L, allocs1[2].amountPaise)
        assertEquals(1L, allocs1.sumOf { it.amountPaise })
    }

    // ------------------------------------------------------------------------
    // Scenario 4: Allocation sum strictly equals original amount in paise across various amounts
    // ------------------------------------------------------------------------
    @Test
    fun test04_AllocationSumStrictlyEqualsOriginalAmountInPaiseAcrossLargeSample() {
        val startDate = makeCalendarTimestamp(2026, Calendar.JANUARY, 1)
        val testAmounts = listOf(
            0L, 1L, 2L, 5L, 7L, 11L, 99L, 100L, 101L, 999L, 1000L,
            7777L, 1234567L, 100000000L // ₹10,00,000.00
        )
        val testDurations = listOf(1, 2, 3, 4, 7, 10, 13, 28, 30, 31, 60, 90, 180, 365)

        for (amount in testAmounts) {
            for (duration in testDurations) {
                val allocs = ExpenseAllocationCalculator.calculateAllocations(
                    amountPaise = amount,
                    durationDays = duration,
                    startDateMillis = startDate,
                    timeZone = defaultTz
                )
                val sum = allocs.sumOf { it.amountPaise }
                assertEquals(
                    "Sum must strictly equal total amount $amount for duration $duration",
                    amount,
                    sum
                )
            }
        }
    }

    // ------------------------------------------------------------------------
    // Scenario 5: Single-day expense (duration = 1 or spreading disabled)
    // ------------------------------------------------------------------------
    @Test
    fun test05_SingleDayExpense_DurationOneOrDisabled_ActsAsNormalExpense() = runBlocking {
        val txnDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19, 14)
        val normalTxn = Transaction(
            id = 0L,
            title = "Lunch",
            amountPaise = 25000L,
            categoryId = 1L,
            transactionDate = txnDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = false,
            spreadDurationDays = 1
        )
        val savedId = transactionRepository.saveTransaction(normalTxn)
        val loaded = transactionRepository.getTransactionById(savedId)
        assertNotNull(loaded)
        assertFalse(loaded!!.isSpread)
        assertEquals(1, loaded.spreadDurationDays)
        assertTrue(loaded.allocations.isEmpty())

        // Period check
        val dayStart = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19, 0)
        val dayEnd = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 20, 0)
        val nextDayStart = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 20, 0)
        val nextDayEnd = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 21, 0)

        val expOnDay = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, dayStart, dayEnd)
        assertEquals(25000L, expOnDay)

        val expNextDay = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, nextDayStart, nextDayEnd)
        assertEquals(0L, expNextDay)
    }

    // ------------------------------------------------------------------------
    // Scenario 6: Expense crossing month boundaries (allocations split correctly across months)
    // ------------------------------------------------------------------------
    @Test
    fun test06_ExpenseCrossingMonthBoundaries_AllocationsSplitCorrectly() = runBlocking {
        // Bought 28 Nov 2026 for 5 days: 28 Nov, 29 Nov, 30 Nov, 1 Dec, 2 Dec.
        // Total = ₹50.00 (5000 paise) -> 1000 paise per day.
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 28, 10)
        val spreadTxn = Transaction(
            id = 0L,
            title = "Bulk Groceries",
            amountPaise = 5000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 5,
            spreadStartDate = startDate
        )
        val savedId = transactionRepository.saveTransaction(spreadTxn)
        val loaded = transactionRepository.getTransactionById(savedId)
        assertNotNull(loaded)
        assertEquals(5, loaded!!.allocations.size)

        // Month of November bounds
        val novStart = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 1, 0)
        val novEnd = makeCalendarTimestamp(2026, Calendar.DECEMBER, 1, 0)

        // Month of December bounds
        val decStart = makeCalendarTimestamp(2026, Calendar.DECEMBER, 1, 0)
        val decEnd = makeCalendarTimestamp(2027, Calendar.JANUARY, 1, 0)

        val novExpense = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, novStart, novEnd)
        val decExpense = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, decStart, decEnd)

        // 3 days in Nov (28, 29, 30) = 3000 paise
        assertEquals(3000L, novExpense)
        // 2 days in Dec (1, 2) = 2000 paise
        assertEquals(2000L, decExpense)
        assertEquals(5000L, novExpense + decExpense)
    }

    // ------------------------------------------------------------------------
    // Scenario 7: Expense crossing year boundaries (allocations split correctly across years)
    // ------------------------------------------------------------------------
    @Test
    fun test07_ExpenseCrossingYearBoundaries_AllocationsSplitCorrectly() = runBlocking {
        // Bought 30 Dec 2026 for 4 days: 30 Dec, 31 Dec, 1 Jan 2027, 2 Jan 2027.
        // Total = ₹40.00 (4000 paise) -> 1000 paise per day.
        val startDate = makeCalendarTimestamp(2026, Calendar.DECEMBER, 30, 16)
        val spreadTxn = Transaction(
            id = 0L,
            title = "New Year Supplies",
            amountPaise = 4000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 4,
            spreadStartDate = startDate
        )
        val savedId = transactionRepository.saveTransaction(spreadTxn)
        val loaded = transactionRepository.getTransactionById(savedId)
        assertNotNull(loaded)
        assertEquals(4, loaded!!.allocations.size)

        val year2026Start = makeCalendarTimestamp(2026, Calendar.JANUARY, 1, 0)
        val year2026End = makeCalendarTimestamp(2027, Calendar.JANUARY, 1, 0)

        val year2027Start = makeCalendarTimestamp(2027, Calendar.JANUARY, 1, 0)
        val year2027End = makeCalendarTimestamp(2028, Calendar.JANUARY, 1, 0)

        val exp2026 = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, year2026Start, year2026End)
        val exp2027 = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, year2027Start, year2027End)

        assertEquals(2000L, exp2026) // Dec 30, 31
        assertEquals(2000L, exp2027) // Jan 1, 2
        assertEquals(4000L, exp2026 + exp2027)
    }

    // ------------------------------------------------------------------------
    // Scenario 8: Editing amount and duration (recalculates allocations atomically)
    // ------------------------------------------------------------------------
    @Test
    fun test08_EditingAmountAndDuration_RecalculatesAllocationsAtomically() = runBlocking {
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19, 10)
        val original = Transaction(
            id = 0L,
            title = "Milk",
            amountPaise = 6000L, // ₹60 over 5 days
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 5,
            spreadStartDate = startDate
        )
        val id = transactionRepository.saveTransaction(original)
        val allocsBefore = database.expenseAllocationDao().getAllocationsForTransaction(id)
        assertEquals(5, allocsBefore.size)
        assertEquals(6000L, allocsBefore.sumOf { it.amountPaise })

        // Edit to ₹120.00 (12000 paise) over 4 days
        val loaded = transactionRepository.getTransactionById(id)!!
        val updated = loaded.copy(
            amountPaise = 12000L,
            spreadDurationDays = 4,
            allocations = emptyList() // Trigger recalculation
        )
        transactionRepository.saveTransaction(updated)

        val allocsAfter = database.expenseAllocationDao().getAllocationsForTransaction(id)
        assertEquals("Should now have 4 allocations", 4, allocsAfter.size)
        assertEquals("Total allocated must equal 12000 paise", 12000L, allocsAfter.sumOf { it.amountPaise })
        allocsAfter.forEach {
            assertEquals("Each day should be 3000 paise (₹30.00)", 3000L, it.amountPaise)
        }
    }

    // ------------------------------------------------------------------------
    // Scenario 9: Changing spread expense back to normal (cleans up allocations, returns to single day)
    // ------------------------------------------------------------------------
    @Test
    fun test09_ChangingSpreadExpenseBackToNormal_CleansUpAllocations() = runBlocking {
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19, 10)
        val spread = Transaction(
            id = 0L,
            title = "Milk",
            amountPaise = 6000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 5,
            spreadStartDate = startDate
        )
        val id = transactionRepository.saveTransaction(spread)
        assertTrue(database.expenseAllocationDao().getAllocationsForTransaction(id).isNotEmpty())

        // User toggles off "Spread this expense across days"
        val loaded = transactionRepository.getTransactionById(id)!!
        val normal = loaded.copy(
            isSpread = false,
            spreadDurationDays = 1,
            spreadStartDate = null,
            spreadEndDate = null,
            allocations = emptyList()
        )
        transactionRepository.saveTransaction(normal)

        val allocsAfter = database.expenseAllocationDao().getAllocationsForTransaction(id)
        assertTrue("Allocations must be completely cleaned up", allocsAfter.isEmpty())

        val reloaded = transactionRepository.getTransactionById(id)!!
        assertFalse(reloaded.isSpread)
        assertEquals(1, reloaded.spreadDurationDays)
        assertTrue(reloaded.allocations.isEmpty())
    }

    // ------------------------------------------------------------------------
    // Scenario 10: Deleting a spread expense (cascades and deletes allocations)
    // ------------------------------------------------------------------------
    @Test
    fun test10_DeletingSpreadExpense_CascadesAndDeletesAllocations() = runBlocking {
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19, 10)
        val spread = Transaction(
            id = 0L,
            title = "Vegetables",
            amountPaise = 7000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 7,
            spreadStartDate = startDate
        )
        val id = transactionRepository.saveTransaction(spread)
        assertEquals(7, database.expenseAllocationDao().getAllocationsForTransaction(id).size)

        // Delete the transaction
        transactionRepository.deleteTransaction(id)

        val remainingAllocations = database.expenseAllocationDao().getAllocationsForTransaction(id)
        assertTrue("Foreign key CASCADE must delete all linked allocations", remainingAllocations.isEmpty())
    }

    // ------------------------------------------------------------------------
    // Scenario 11: Backup and restore (exports allocations, restores correctly)
    // ------------------------------------------------------------------------
    @Test
    fun test11_BackupAndRestore_ExportsAndRestoresAllocations() = runBlocking {
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19, 10)
        val spread = Transaction(
            id = 0L,
            title = "Weekly Fruit Basket",
            amountPaise = 7000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 7,
            spreadStartDate = startDate
        )
        val originalId = transactionRepository.saveTransaction(spread)

        val exportedJson = backupRestoreRepository.exportToJson()
        val rootObj = JSONObject(exportedJson)
        val txnObj = rootObj.getJSONArray("transactions").getJSONObject(0)
        assertTrue(txnObj.getBoolean("isSpread"))
        assertEquals(7, txnObj.getInt("spreadDurationDays"))
        assertTrue(txnObj.has("allocations"))
        assertEquals(7, txnObj.getJSONArray("allocations").length())

        // Clear all tables before restore
        database.transactionDao().deleteTransactionById(originalId)
        database.expenseAllocationDao().clearAllAllocations()
        assertTrue(transactionRepository.getAllTransactionsList().isEmpty())

        // Restore
        val restoreSuccess = backupRestoreRepository.restoreFromJson(exportedJson)
        assertTrue("Restore must succeed", restoreSuccess)

        val restoredTxns = transactionRepository.getAllTransactionsList()
        assertEquals(1, restoredTxns.size)
        val restored = restoredTxns[0]
        assertTrue(restored.isSpread)
        assertEquals(7, restored.spreadDurationDays)
        assertEquals(7, restored.allocations.size)
        assertEquals(7000L, restored.allocations.sumOf { it.amountPaise })
        restored.allocations.forEach {
            assertEquals(1000L, it.amountPaise)
        }
    }

    // ------------------------------------------------------------------------
    // Scenario 12: Daily analytics (uses daily allocation amount)
    // ------------------------------------------------------------------------
    @Test
    fun test12_DailyAnalytics_UsesDailyAllocationAmount() = runBlocking {
        // ₹60 over 3 days (Nov 10, 11, 12) -> ₹20/day
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 10, 8)
        val spread = Transaction(
            id = 0L,
            title = "Bread & Eggs",
            amountPaise = 6000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 3,
            spreadStartDate = startDate
        )
        val id = transactionRepository.saveTransaction(spread)
        val loaded = transactionRepository.getTransactionById(id)!!

        val nov10Start = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 10, 0)
        val nov10End = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 11, 0)
        val nov11Start = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 11, 0)
        val nov11End = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 12, 0)
        val nov12Start = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 12, 0)
        val nov12End = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 13, 0)

        val expNov10 = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, nov10Start, nov10End)
        val expNov11 = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, nov11Start, nov11End)
        val expNov12 = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, nov12Start, nov12End)

        assertEquals(2000L, expNov10)
        assertEquals(2000L, expNov11)
        assertEquals(2000L, expNov12)
    }

    // ------------------------------------------------------------------------
    // Scenario 13: Weekly analytics (uses weekly allocation amounts)
    // ------------------------------------------------------------------------
    @Test
    fun test13_WeeklyAnalytics_UsesAllocationsInsideWeek() = runBlocking {
        // Nov 13, 2026 is Friday.
        // Week 1 (Mon Nov 9 - Sun Nov 15) covers Nov 13, 14, 15 (3 days)
        // Week 2 (Mon Nov 16 - Sun Nov 22) covers Nov 16, 17 (2 days)
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 13, 10)
        val spread = Transaction(
            id = 0L,
            title = "Weekend + Week Supplies",
            amountPaise = 5000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 5,
            spreadStartDate = startDate
        )
        val id = transactionRepository.saveTransaction(spread)
        val loaded = transactionRepository.getTransactionById(id)!!

        val week1Start = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 9, 0)
        val week1End = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 16, 0)

        val week2Start = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 16, 0)
        val week2End = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 23, 0)

        val expWeek1 = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, week1Start, week1End)
        val expWeek2 = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, week2Start, week2End)

        assertEquals(3000L, expWeek1) // 3 days in Week 1
        assertEquals(2000L, expWeek2) // 2 days in Week 2
        assertEquals(5000L, expWeek1 + expWeek2)
    }

    // ------------------------------------------------------------------------
    // Scenario 14: Monthly analytics (allocations inside month)
    // ------------------------------------------------------------------------
    @Test
    fun test14_MonthlyAnalytics_AllocationsInsideMonthCountedAccurately() = runBlocking {
        // Starts Oct 30 (3 days: Oct 30, Oct 31, Nov 1) for ₹90.00 (9000 paise) -> ₹30/day
        val startDate = makeCalendarTimestamp(2026, Calendar.OCTOBER, 30, 9)
        val spread = Transaction(
            id = 0L,
            title = "Monthly Transition Goods",
            amountPaise = 9000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 3,
            spreadStartDate = startDate
        )
        val id = transactionRepository.saveTransaction(spread)
        val loaded = transactionRepository.getTransactionById(id)!!

        val octStart = makeCalendarTimestamp(2026, Calendar.OCTOBER, 1, 0)
        val octEnd = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 1, 0)

        val novStart = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 1, 0)
        val novEnd = makeCalendarTimestamp(2026, Calendar.DECEMBER, 1, 0)

        val octExp = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, octStart, octEnd)
        val novExp = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, novStart, novEnd)

        assertEquals(6000L, octExp) // Oct 30, 31 (2 days)
        assertEquals(3000L, novExp) // Nov 1 (1 day)
    }

    // ------------------------------------------------------------------------
    // Scenario 15: Category analytics (allocated amounts attributed to category in period)
    // ------------------------------------------------------------------------
    @Test
    fun test15_CategoryAnalytics_AllocatedAmountsAttributedToCategoryInPeriod() = runBlocking {
        // Category Food (id = 1), ₹90.00 over 3 days (Nov 29, Nov 30, Dec 1)
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 29, 12)
        val spread = Transaction(
            id = 0L,
            title = "Food Groceries",
            amountPaise = 9000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 3,
            spreadStartDate = startDate
        )
        val id = transactionRepository.saveTransaction(spread)
        val loaded = transactionRepository.getTransactionById(id)!!

        val novStart = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 1, 0)
        val novEnd = makeCalendarTimestamp(2026, Calendar.DECEMBER, 1, 0)

        val categoryTotalsNov = PersonalFinancialAggregator.calculatePersonalCategoryTotalsForPeriod(
            listOf(loaded),
            novStart,
            novEnd
        )

        assertEquals(6000L, categoryTotalsNov[1L]) // 2 days in Nov
    }

    // ------------------------------------------------------------------------
    // Scenario 16: Budget calculations (allocated amounts attributed to budget period)
    // ------------------------------------------------------------------------
    @Test
    fun test16_BudgetPeriodCalculations_CountAllocatedAmounts() = runBlocking {
        // Budget Period: Nov 1 to Nov 15
        val budgetStart = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 1, 0)
        val budgetEnd = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 16, 0)

        // Expense 1: ₹100 over 10 days starting Nov 10 (Nov 10 to Nov 19) -> ₹10/day
        // Days inside budget: Nov 10, 11, 12, 13, 14, 15 (6 days = ₹60.00 = 6000 paise)
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 10, 10)
        val spread = Transaction(
            id = 0L,
            title = "10-day Ration",
            amountPaise = 10000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 10,
            spreadStartDate = startDate
        )
        val id = transactionRepository.saveTransaction(spread)
        val loaded = transactionRepository.getTransactionById(id)!!

        val totalInBudget = PersonalFinancialAggregator.calculateTotalPersonalExpenseForPeriod(
            listOf(loaded),
            budgetStart,
            budgetEnd
        )
        assertEquals(6000L, totalInBudget)
    }

    // ------------------------------------------------------------------------
    // Scenario 17: Calendar summaries (day-wise allocation in MonthlyCalendarEngine)
    // ------------------------------------------------------------------------
    @Test
    fun test17_MonthlyCalendarSummaries_DayWiseAllocationInMonthlyCalendarEngine() = runBlocking {
        // Milk ₹60 over 5 days (Nov 19 to Nov 23) -> ₹12/day
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19, 10)
        val spread = Transaction(
            id = 0L,
            title = "Milk",
            amountPaise = 6000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 5,
            spreadStartDate = startDate
        )
        val id = transactionRepository.saveTransaction(spread)
        val loaded = transactionRepository.getTransactionById(id)!!

        val calendarSummaries = MonthlyCalendarEngine.computeDailySummaries(
            transactions = listOf(loaded),
            year = 2026,
            month = Calendar.NOVEMBER,
            timeZone = defaultTz
        )

        // Nov 18 should have 0
        assertEquals(0L, calendarSummaries[18]?.totalExpensePaise ?: 0L)
        // Nov 19 to 23 should each have ₹12 (1200 paise)
        for (day in 19..23) {
            val daySummary = calendarSummaries[day]
            assertNotNull("Day $day must have summary", daySummary)
            assertEquals("Day $day must allocate 1200 paise", 1200L, daySummary!!.totalExpensePaise)
        }
        // Nov 24 should have 0
        assertEquals(0L, calendarSummaries[24]?.totalExpensePaise ?: 0L)
    }

    // ------------------------------------------------------------------------
    // Scenario 18: Shared expenses and Split Pro ledger regression
    // ------------------------------------------------------------------------
    @Test
    fun test18_SharedExpensesAndSplitProLedgerRegression_DebtsIntactPersonalSpread() = runBlocking {
        // Create friend Rahul
        val friendId = friendRepository.addFriend(
            Friend(id = 0L, name = "Rahul", phoneNumber = "9876543210")
        )

        // ₹100 grocery bought by Me on Nov 19, split 50/50 with Rahul, spread over 5 days
        val startDate = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19, 10)
        val participants = listOf(
            TransactionParticipant(
                id = 0L,
                transactionId = 0L,
                friendId = null, // Me
                friendName = "Me",
                shareAmountPaise = 5000L,
                percentage = 50.0,
                splitMethod = SplitMethod.EQUAL
            ),
            TransactionParticipant(
                id = 0L,
                transactionId = 0L,
                friendId = friendId,
                friendName = "Rahul",
                shareAmountPaise = 5000L,
                percentage = 50.0,
                splitMethod = SplitMethod.EQUAL
            )
        )
        val sharedSpreadTxn = Transaction(
            id = 0L,
            title = "Shared Groceries",
            amountPaise = 10000L,
            categoryId = 1L,
            transactionDate = startDate,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null, // Paid by Me
            participants = participants,
            splitMethod = SplitMethod.EQUAL,
            transactionType = TransactionType.EXPENSE,
            isSpread = true,
            spreadDurationDays = 5,
            spreadStartDate = startDate
        )
        val savedId = transactionRepository.saveTransaction(sharedSpreadTxn)
        val loaded = transactionRepository.getTransactionById(savedId)!!

        // 1. Ledger calculation: Rahul owes Me ₹50 (5000 paise) based on full transaction share
        val unsettled = LedgerCalculator.calculateUnsettledExpenses(friendId, listOf(loaded), emptyList<Settlement>())
        assertEquals(1, unsettled.size)
        assertEquals(5000L, unsettled[0].remainingOutstandingPaise)
        assertEquals(ExpenseObligationDirection.THEY_OWE_ME, unsettled[0].direction)

        // 2. Personal spending calculation: Me's 5000 paise share is spread across 5 days (1000 paise/day)
        val day1Start = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 19, 0)
        val day1End = makeCalendarTimestamp(2026, Calendar.NOVEMBER, 20, 0)

        val meExpenseDay1 = PersonalFinancialAggregator.calculatePersonalExpenseForPeriod(loaded, day1Start, day1End)
        assertEquals(1000L, meExpenseDay1)
    }

    // ------------------------------------------------------------------------
    // Scenario 19: Old database migration (MIGRATION_14_15 preserves existing data)
    // ------------------------------------------------------------------------
    @Test
    fun test19_OldDatabaseMigration_Version14To15PreservesExistingData() {
        val tempDbFile = File.createTempFile("test_migration_14_15", ".db")
        tempDbFile.deleteOnExit()

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(tempDbFile.absolutePath)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Create v14 schema with existing transaction
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `transactions` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `title` TEXT NOT NULL,
                                `description` TEXT NOT NULL,
                                `amountPaise` INTEGER NOT NULL,
                                `categoryId` INTEGER NOT NULL,
                                `transactionDate` INTEGER NOT NULL,
                                `paymentMethod` TEXT NOT NULL,
                                `accountSource` TEXT,
                                `paidByFriendId` INTEGER,
                                `splitMethod` TEXT NOT NULL,
                                `transactionType` TEXT NOT NULL,
                                `transactionStatus` TEXT NOT NULL,
                                `source` TEXT NOT NULL,
                                `merchantName` TEXT,
                                `referenceNumber` TEXT,
                                `upiReferenceNumber` TEXT,
                                `groupId` INTEGER,
                                `expenseGroupId` INTEGER,
                                `expenseGroupName` TEXT,
                                `billImagePath` TEXT,
                                `latitude` REAL,
                                `longitude` REAL,
                                `accuracyMeters` REAL,
                                `locationCapturedAt` INTEGER,
                                `locationSource` TEXT,
                                `locationLabel` TEXT,
                                `googleMapsUrl` TEXT,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL
                            )
                        """.trimIndent())

                        db.execSQL("""
                            INSERT INTO `transactions` (`id`, `title`, `description`, `amountPaise`, `categoryId`,
                            `transactionDate`, `paymentMethod`, `splitMethod`, `transactionType`, `transactionStatus`,
                            `source`, `createdAt`, `updatedAt`)
                            VALUES (1, 'Legacy Expense', 'Pre-migration entry', 5000, 1, 1700000000, 'UPI', 'EQUAL',
                            'EXPENSE', 'ACTIVE', 'MANUAL', 1700000000, 1700000000)
                        """.trimIndent())
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = helper.writableDatabase
        // Apply migration 14 -> 15
        RupeeFlowDatabase.MIGRATION_14_15.migrate(db)

        // Verify expense_allocations table exists
        val allocTableCheck = db.query("SELECT COUNT(*) FROM expense_allocations")
        assertTrue(allocTableCheck.moveToFirst())
        assertEquals(0, allocTableCheck.getInt(0))
        allocTableCheck.close()

        // Verify existing transaction preserved with default spreading columns
        val txnCursor = db.query("SELECT id, title, amountPaise, isSpread, spreadDurationDays FROM transactions WHERE id = 1")
        assertTrue(txnCursor.moveToFirst())
        assertEquals(1L, txnCursor.getLong(0))
        assertEquals("Legacy Expense", txnCursor.getString(1))
        assertEquals(5000L, txnCursor.getLong(2))
        assertEquals(0, txnCursor.getInt(3)) // isSpread = 0 (false)
        assertEquals(1, txnCursor.getInt(4)) // spreadDurationDays = 1
        txnCursor.close()

        db.close()
    }

    // ------------------------------------------------------------------------
    // Scenario 20: Old backup compatibility (older backup without spreading restores cleanly)
    // ------------------------------------------------------------------------
    @Test
    fun test20_OldBackupCompatibility_BackupsWithoutSpreadingRestoreCleanly() = runBlocking {
        // Construct an older version backup JSON without isSpread or allocations
        val legacyBackupJson = JSONObject().apply {
            put("version", 4)
            put("appName", "RupeeFlow")
            put("exportTimestamp", System.currentTimeMillis())

            val catArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 1L)
                    put("name", "Food")
                    put("iconName", "restaurant")
                    put("colorHex", "#F59E0B")
                    put("isDefault", true)
                })
            }
            put("categories", catArray)

            val txnArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 101L)
                    put("title", "Legacy Dinner")
                    put("description", "From version 4 backup")
                    put("amountPaise", 45000L)
                    put("categoryId", 1L)
                    put("transactionDate", 1700000000L)
                    put("paymentMethod", "UPI")
                    put("splitMethod", "EQUAL")
                    put("transactionType", "EXPENSE")
                    put("transactionStatus", "ACTIVE")
                    put("source", "MANUAL")
                    put("createdAt", 1700000000L)
                })
            }
            put("transactions", txnArray)
        }.toString()

        val restoreResult = backupRestoreRepository.restoreFromJson(legacyBackupJson)
        assertTrue("Older backup without spreading fields must restore cleanly", restoreResult)

        val restoredTxn = transactionRepository.getTransactionById(101L)
        assertNotNull(restoredTxn)
        assertEquals("Legacy Dinner", restoredTxn!!.title)
        assertEquals(45000L, restoredTxn.amountPaise)
        assertFalse("Old transaction should have isSpread=false", restoredTxn.isSpread)
        assertEquals("Old transaction should have duration=1", 1, restoredTxn.spreadDurationDays)
        assertTrue("Old transaction should have empty allocations", restoredTxn.allocations.isEmpty())
    }
}

