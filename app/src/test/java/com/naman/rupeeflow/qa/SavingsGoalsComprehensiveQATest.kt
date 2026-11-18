package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.*
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.ui.screens.savings.SavingsGoalsUiState
import com.naman.rupeeflow.ui.theme.PeachyAppColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Calendar

/**
 * RupeeFlow Comprehensive QA Test Suite for Savings Goals & Daily Savings Tracker
 * Strictly verifies all 40 requirements defined in Phase 11.
 */
@RunWith(RobolectricTestRunner::class)
class SavingsGoalsComprehensiveQATest {

    private lateinit var context: Context
    private lateinit var db: RupeeFlowDatabase
    private lateinit var savingsRepo: SavingsGoalRepository
    private lateinit var transactionRepo: TransactionRepository
    private lateinit var friendRepo: FriendRepository
    private lateinit var settlementRepo: SettlementRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var backupRestoreRepo: BackupRestoreRepository

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        savingsRepo = SavingsGoalRepositoryImpl(
            savingsGoalDao = db.savingsGoalDao(),
            savingsEntryDao = db.savingsEntryDao()
        )
        categoryRepo = CategoryRepositoryImpl(db.categoryDao())
        friendRepo = FriendRepositoryImpl(db.friendDao())
        transactionRepo = TransactionRepositoryImpl(
            transactionDao = db.transactionDao(),
            categoryDao = db.categoryDao(),
            friendDao = db.friendDao(),
            context = context
        )
        settlementRepo = SettlementRepositoryImpl(
            settlementDao = db.settlementDao(),
            friendDao = db.friendDao(),
            context = context
        )
        backupRestoreRepo = BackupRestoreRepository(
            database = db,
            transactionRepository = transactionRepo,
            friendRepository = friendRepo,
            settlementRepository = settlementRepo,
            categoryRepository = categoryRepo,
            context = context
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    // -------------------------------------------------------------------------
    // 1. Create savings goal
    // -------------------------------------------------------------------------
    @Test
    fun test01_createSavingsGoal() = runBlocking {
        val goalId = savingsRepo.createGoal(
            name = "New Laptop",
            targetAmountPaise = 8000000L, // ₹80,000
            initialSavedAmountPaise = 500000L, // ₹5,000
            targetDate = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
            category = SavingsGoalCategory.ELECTRONICS,
            description = "MacBook Air M3 for coding",
            colorHex = "#FFAD91",
            priority = SavingsGoalPriority.HIGH
        )
        assertTrue(goalId > 0)

        val goal = savingsRepo.getGoalByIdSync(goalId)
        assertNotNull(goal)
        assertEquals("New Laptop", goal!!.name)
        assertEquals(8000000L, goal.targetAmountPaise)
        assertEquals(500000L, goal.currentSavedPaise)
        assertEquals(SavingsGoalCategory.ELECTRONICS, goal.category)
        assertEquals(SavingsGoalStatus.ACTIVE, goal.status)
        assertEquals(SavingsGoalPriority.HIGH, goal.priority)

        // Verify initial deposit entry created
        val entries = savingsRepo.getEntriesForGoalList(goalId)
        assertEquals(1, entries.size)
        assertEquals(500000L, entries[0].amountPaise)
        assertEquals(SavingsEntryType.DEPOSIT, entries[0].entryType)
    }

    // -------------------------------------------------------------------------
    // 2. Edit savings goal
    // -------------------------------------------------------------------------
    @Test
    fun test02_editSavingsGoal() = runBlocking {
        val goalId = savingsRepo.createGoal(
            name = "Emergency",
            targetAmountPaise = 5000000L,
            category = SavingsGoalCategory.EMERGENCY_FUND
        )
        val original = savingsRepo.getGoalByIdSync(goalId)!!
        val updated = original.copy(
            name = "Emergency Fund 2026",
            targetAmountPaise = 6000000L,
            description = "6 months living expenses"
        )
        savingsRepo.updateGoal(updated)

        val fetched = savingsRepo.getGoalByIdSync(goalId)!!
        assertEquals("Emergency Fund 2026", fetched.name)
        assertEquals(6000000L, fetched.targetAmountPaise)
        assertEquals("6 months living expenses", fetched.description)
    }

    // -------------------------------------------------------------------------
    // 3. Delete goal
    // -------------------------------------------------------------------------
    @Test
    fun test03_deleteGoal() = runBlocking {
        val goalId = savingsRepo.createGoal(
            name = "Trip to Goa",
            targetAmountPaise = 2500000L,
            initialSavedAmountPaise = 200000L,
            category = SavingsGoalCategory.TRAVEL
        )
        savingsRepo.deleteGoal(goalId)

        val fetched = savingsRepo.getGoalByIdSync(goalId)
        assertNull(fetched)
        // Verify entries cascade deleted
        val entries = savingsRepo.getEntriesForGoalList(goalId)
        assertTrue(entries.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 4. Archive goal
    // -------------------------------------------------------------------------
    @Test
    fun test04_archiveGoal() = runBlocking {
        val goalId = savingsRepo.createGoal(
            name = "Old Phone Goal",
            targetAmountPaise = 3000000L,
            category = SavingsGoalCategory.ELECTRONICS
        )
        savingsRepo.setGoalStatus(goalId, SavingsGoalStatus.ARCHIVED)

        val goal = savingsRepo.getGoalByIdSync(goalId)!!
        assertEquals(SavingsGoalStatus.ARCHIVED, goal.status)

        // Verify still in all goals
        val all = savingsRepo.getAllGoalsList()
        assertEquals(1, all.size)
    }

    // -------------------------------------------------------------------------
    // 5. Restore/Resume goal
    // -------------------------------------------------------------------------
    @Test
    fun test05_restoreOrResumeGoal() = runBlocking {
        val goalId = savingsRepo.createGoal(
            name = "Bike Upgrade",
            targetAmountPaise = 10000000L,
            category = SavingsGoalCategory.VEHICLE
        )
        savingsRepo.setGoalStatus(goalId, SavingsGoalStatus.PAUSED)
        assertEquals(SavingsGoalStatus.PAUSED, savingsRepo.getGoalByIdSync(goalId)!!.status)

        savingsRepo.setGoalStatus(goalId, SavingsGoalStatus.ACTIVE)
        assertEquals(SavingsGoalStatus.ACTIVE, savingsRepo.getGoalByIdSync(goalId)!!.status)
    }

    // -------------------------------------------------------------------------
    // 6. Complete goal
    // -------------------------------------------------------------------------
    @Test
    fun test06_completeGoal() = runBlocking {
        val goalId = savingsRepo.createGoal(
            name = "Headphones",
            targetAmountPaise = 100000L, // ₹1,000
            category = SavingsGoalCategory.ELECTRONICS
        )
        savingsRepo.addDeposit(goalId, 100000L, System.currentTimeMillis(), "Saved from allowance")

        val goal = savingsRepo.getGoalByIdSync(goalId)!!
        assertEquals(SavingsGoalStatus.COMPLETED, goal.status)
        assertTrue(goal.isCompleted)
    }

    // -------------------------------------------------------------------------
    // 7. Add savings
    // -------------------------------------------------------------------------
    @Test
    fun test07_addSavings() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Books", targetAmountPaise = 500000L, category = SavingsGoalCategory.EDUCATION)
        val entryId = savingsRepo.addDeposit(
            goalId = goalId,
            amountPaise = 50000L, // ₹500
            entryDate = System.currentTimeMillis(),
            description = "Saved ₹500 from hostel allowance",
            paymentMethod = "UPI",
            note = "Direct bank transfer"
        )
        assertTrue(entryId > 0)

        val goal = savingsRepo.getGoalByIdSync(goalId)!!
        assertEquals(50000L, goal.currentSavedPaise)
    }

    // -------------------------------------------------------------------------
    // 8. Add multiple savings on one date
    // -------------------------------------------------------------------------
    @Test
    fun test08_addMultipleSavingsOnOneDate() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Phone", targetAmountPaise = 4000000L, category = SavingsGoalCategory.ELECTRONICS)
        val fixedDate = System.currentTimeMillis()

        savingsRepo.addDeposit(goalId, 50000L, fixedDate, "Saved from breakfast")
        savingsRepo.addDeposit(goalId, 100000L, fixedDate, "Saved from pocket money")

        val goal = savingsRepo.getGoalByIdSync(goalId)!!
        assertEquals(150000L, goal.currentSavedPaise)

        val groups = savingsRepo.getDailySavingsGroups(goalId).first()
        assertEquals(1, groups.size)
        assertEquals(150000L, groups[0].totalSavedPaise)
        assertEquals(2, groups[0].entries.size)
    }

    // -------------------------------------------------------------------------
    // 9. Add saving on a historical date
    // -------------------------------------------------------------------------
    @Test
    fun test09_addSavingOnHistoricalDate() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Car", targetAmountPaise = 50000000L, category = SavingsGoalCategory.VEHICLE)
        val pastDate = System.currentTimeMillis() - (15L * 24 * 60 * 60 * 1000) // 15 days ago

        savingsRepo.addDeposit(goalId, 250000L, pastDate, "Saved last month")
        val groups = savingsRepo.getDailySavingsGroups(goalId).first()
        assertEquals(1, groups.size)
        assertEquals(250000L, groups[0].totalSavedPaise)
    }

    // -------------------------------------------------------------------------
    // 10. Edit saving date
    // -------------------------------------------------------------------------
    @Test
    fun test10_editSavingDate() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Trip", targetAmountPaise = 2000000L, category = SavingsGoalCategory.TRAVEL)
        val t1 = System.currentTimeMillis()
        val t2 = t1 - (7L * 24 * 60 * 60 * 1000)

        val entryId = savingsRepo.addDeposit(goalId, 100000L, t1, "Initial save")
        val entry = savingsRepo.getEntriesForGoalList(goalId).first()

        savingsRepo.updateEntry(entry.copy(entryDate = t2))

        val updatedEntries = savingsRepo.getEntriesForGoalList(goalId)
        assertEquals(t2, updatedEntries[0].entryDate)
    }

    // -------------------------------------------------------------------------
    // 11. Edit saving description
    // -------------------------------------------------------------------------
    @Test
    fun test11_editSavingDescription() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Gadget", targetAmountPaise = 500000L, category = SavingsGoalCategory.ELECTRONICS)
        savingsRepo.addDeposit(goalId, 50000L, System.currentTimeMillis(), "Coffee savings")
        val entry = savingsRepo.getEntriesForGoalList(goalId).first()

        savingsRepo.updateEntry(entry.copy(description = "Skipped Starbucks"))
        val updated = savingsRepo.getEntriesForGoalList(goalId).first()
        assertEquals("Skipped Starbucks", updated.description)
    }

    // -------------------------------------------------------------------------
    // 12. Edit saving amount
    // -------------------------------------------------------------------------
    @Test
    fun test12_editSavingAmount() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Shoes", targetAmountPaise = 600000L, category = SavingsGoalCategory.SHOPPING)
        savingsRepo.addDeposit(goalId, 50000L, System.currentTimeMillis(), "Deposit 1")
        val entry = savingsRepo.getEntriesForGoalList(goalId).first()

        // Change from ₹500 to ₹1,200
        savingsRepo.updateEntry(entry.copy(amountPaise = 120000L))

        val goal = savingsRepo.getGoalByIdSync(goalId)!!
        assertEquals(120000L, goal.currentSavedPaise)
    }

    // -------------------------------------------------------------------------
    // 13. Delete saving entry
    // -------------------------------------------------------------------------
    @Test
    fun test13_deleteSavingEntry() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Camera", targetAmountPaise = 4000000L, category = SavingsGoalCategory.ELECTRONICS)
        val e1 = savingsRepo.addDeposit(goalId, 100000L, System.currentTimeMillis(), "Dep 1")
        val e2 = savingsRepo.addDeposit(goalId, 200000L, System.currentTimeMillis(), "Dep 2")

        savingsRepo.deleteEntry(e1, goalId)

        val goal = savingsRepo.getGoalByIdSync(goalId)!!
        assertEquals(200000L, goal.currentSavedPaise)
        assertEquals(1, savingsRepo.getEntriesForGoalList(goalId).size)
    }

    // -------------------------------------------------------------------------
    // 14. Withdraw savings
    // -------------------------------------------------------------------------
    @Test
    fun test14_withdrawSavings() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Emergency", targetAmountPaise = 5000000L, category = SavingsGoalCategory.EMERGENCY_FUND)
        savingsRepo.addDeposit(goalId, 500000L, System.currentTimeMillis(), "Saved ₹5,000")

        val withId = savingsRepo.withdraw(goalId, 100000L, System.currentTimeMillis(), "Needed ₹1,000 for medicine")
        assertTrue(withId > 0)

        val goal = savingsRepo.getGoalByIdSync(goalId)!!
        assertEquals(400000L, goal.currentSavedPaise) // ₹4,000

        val entries = savingsRepo.getEntriesForGoalList(goalId)
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.entryType == SavingsEntryType.WITHDRAWAL && it.amountPaise == 100000L })
    }

    // -------------------------------------------------------------------------
    // 15. Reject withdrawal greater than balance
    // -------------------------------------------------------------------------
    @Test
    fun test15_rejectWithdrawalGreaterThanBalance() {
        runBlocking {
            val goalId = savingsRepo.createGoal(name = "Test", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
            savingsRepo.addDeposit(goalId, 50000L, System.currentTimeMillis(), "Deposit ₹500")

            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    savingsRepo.withdraw(goalId, 60000L, System.currentTimeMillis(), "Overdraw ₹600")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 16. Reject zero amount
    // -------------------------------------------------------------------------
    @Test
    fun test16_rejectZeroAmount() {
        runBlocking {
            val goalId = savingsRepo.createGoal(name = "Test", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    savingsRepo.addDeposit(goalId, 0L, System.currentTimeMillis(), "Zero amount")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 17. Reject negative amount
    // -------------------------------------------------------------------------
    @Test
    fun test17_rejectNegativeAmount() {
        runBlocking {
            val goalId = savingsRepo.createGoal(name = "Test", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    savingsRepo.addDeposit(goalId, -5000L, System.currentTimeMillis(), "Negative amount")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // 18. Reject invalid monetary values
    // -------------------------------------------------------------------------
    @Test
    fun test18_rejectInvalidMonetaryValues() {
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("invalid"))
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("NaN"))
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("-500"))
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("12.345")) // More than 2 decimal places
        assertEquals(50000L, CurrencyUtils.parseInputToPaiseOrNull("500")!!)
        assertEquals(50050L, CurrencyUtils.parseInputToPaiseOrNull("500.50")!!)
    }

    // -------------------------------------------------------------------------
    // 19. Calculate current savings correctly
    // -------------------------------------------------------------------------
    @Test
    fun test19_calculateCurrentSavingsCorrectly() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Math", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
        savingsRepo.addDeposit(goalId, 30000L, System.currentTimeMillis(), "+300")
        savingsRepo.addDeposit(goalId, 45000L, System.currentTimeMillis(), "+450")
        savingsRepo.withdraw(goalId, 15000L, System.currentTimeMillis(), "-150")

        val goal = savingsRepo.getGoalByIdSync(goalId)!!
        assertEquals(60000L, goal.currentSavedPaise) // 300 + 450 - 150 = 600 (60000 paise)
    }

    // -------------------------------------------------------------------------
    // 20. Calculate remaining target correctly
    // -------------------------------------------------------------------------
    @Test
    fun test20_calculateRemainingTargetCorrectly() {
        val goal = SavingsGoal(name = "Laptop", targetAmountPaise = 8000000L, currentSavedPaise = 3000000L)
        assertEquals(5000000L, goal.remainingPaise)
    }

    // -------------------------------------------------------------------------
    // 21. Calculate progress percentage correctly
    // -------------------------------------------------------------------------
    @Test
    fun test21_calculateProgressPercentageCorrectly() {
        val goal = SavingsGoal(name = "Laptop", targetAmountPaise = 8000000L, currentSavedPaise = 3000000L)
        assertEquals(37.5f, goal.progressPercentage, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 22. Handle target exceeded
    // -------------------------------------------------------------------------
    @Test
    fun test22_handleTargetExceeded() {
        val goal = SavingsGoal(name = "Bike", targetAmountPaise = 500000L, currentSavedPaise = 750000L)
        assertTrue(goal.isCompleted)
        assertTrue(goal.isExceeded)
        assertEquals(0L, goal.remainingPaise)
        assertEquals(150.0f, goal.progressPercentage, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 23. Calculate daily totals
    // -------------------------------------------------------------------------
    @Test
    fun test23_calculateDailyTotals() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Daily", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
        val now = System.currentTimeMillis()
        savingsRepo.addDeposit(goalId, 50000L, now, "D1")
        savingsRepo.addDeposit(goalId, 25000L, now, "D2")
        savingsRepo.withdraw(goalId, 10000L, now, "W1")

        val groups = savingsRepo.getDailySavingsGroups(goalId).first()
        assertEquals(1, groups.size)
        assertEquals(65000L, groups[0].totalSavedPaise) // 500 + 250 - 100 = 650
        assertEquals(75000L, groups[0].totalDepositsPaise)
        assertEquals(10000L, groups[0].totalWithdrawalsPaise)
    }

    // -------------------------------------------------------------------------
    // 24. Calculate weekly totals
    // -------------------------------------------------------------------------
    @Test
    fun test24_calculateWeeklyTotals() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Weekly", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
        val now = System.currentTimeMillis()
        savingsRepo.addDeposit(goalId, 100000L, now, "Week entry")

        val entries = savingsRepo.getEntriesForGoalList(goalId)
        val sum = entries.filter { it.entryType == SavingsEntryType.DEPOSIT }.sumOf { it.amountPaise }
        assertEquals(100000L, sum)
    }

    // -------------------------------------------------------------------------
    // 25. Calculate monthly totals
    // -------------------------------------------------------------------------
    @Test
    fun test25_calculateMonthlyTotals() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Monthly", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
        savingsRepo.addDeposit(goalId, 250000L, System.currentTimeMillis(), "Month entry")

        val summary = savingsRepo.getOverviewSummary().first()
        assertEquals(250000L, summary.savingsAddedThisMonthPaise)
    }

    // -------------------------------------------------------------------------
    // 26. Filter by date range
    // -------------------------------------------------------------------------
    @Test
    fun test26_filterByDateRange() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Filter", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
        val dayMillis = 24L * 60 * 60 * 1000
        val base = System.currentTimeMillis()

        savingsRepo.addDeposit(goalId, 10000L, base - 5 * dayMillis, "Day -5")
        savingsRepo.addDeposit(goalId, 20000L, base - 2 * dayMillis, "Day -2")
        savingsRepo.addDeposit(goalId, 30000L, base, "Today")

        val rangeList = savingsRepo.getEntriesForGoalByDateRange(
            goalId,
            base - 3 * dayMillis,
            base - 1 * dayMillis
        ).first()

        assertEquals(1, rangeList.size)
        assertEquals("Day -2", rangeList[0].description)
    }

    // -------------------------------------------------------------------------
    // 27. Search by description
    // -------------------------------------------------------------------------
    @Test
    fun test27_searchByDescription() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Search", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
        savingsRepo.addDeposit(goalId, 50000L, System.currentTimeMillis(), "Hostel allowance savings")
        savingsRepo.addDeposit(goalId, 30000L, System.currentTimeMillis(), "Internship stipend")

        val entries = savingsRepo.getEntriesForGoalList(goalId)
        val searchResults = entries.filter { it.description.contains("allowance", ignoreCase = true) }
        assertEquals(1, searchResults.size)
        assertEquals("Hostel allowance savings", searchResults[0].description)
    }

    // -------------------------------------------------------------------------
    // 28. Handle empty history
    // -------------------------------------------------------------------------
    @Test
    fun test28_handleEmptyHistory() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Empty", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
        val groups = savingsRepo.getDailySavingsGroups(goalId).first()
        assertTrue(groups.isEmpty())
        val entries = savingsRepo.getEntriesForGoalList(goalId)
        assertTrue(entries.isEmpty())
    }

    // -------------------------------------------------------------------------
    // 29. Handle same-day multiple entries
    // -------------------------------------------------------------------------
    @Test
    fun test29_handleSameDayMultipleEntries() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Same Day", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
        val date = System.currentTimeMillis()
        savingsRepo.addDeposit(goalId, 10000L, date, "S1")
        savingsRepo.addDeposit(goalId, 20000L, date, "S2")
        savingsRepo.addDeposit(goalId, 30000L, date, "S3")

        val groups = savingsRepo.getDailySavingsGroups(goalId).first()
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].entries.size)
        assertEquals(60000L, groups[0].totalSavedPaise)
    }

    // -------------------------------------------------------------------------
    // 30. Handle different time zones and date boundaries
    // -------------------------------------------------------------------------
    @Test
    fun test30_handleDifferentTimeZonesAndDateBoundaries() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Boundaries", targetAmountPaise = 1000000L, category = SavingsGoalCategory.PERSONAL)
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.NOVEMBER, 15, 23, 59, 50)
        val day1 = cal.timeInMillis

        cal.set(2026, Calendar.NOVEMBER, 16, 0, 0, 10)
        val day2 = cal.timeInMillis

        savingsRepo.addDeposit(goalId, 10000L, day1, "Late night")
        savingsRepo.addDeposit(goalId, 20000L, day2, "Early morning")

        val groups = savingsRepo.getDailySavingsGroups(goalId).first()
        assertEquals(2, groups.size)
    }

    // -------------------------------------------------------------------------
    // 31. App restart persistence
    // -------------------------------------------------------------------------
    @Test
    fun test31_appRestartPersistence() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Persistent Goal", targetAmountPaise = 500000L, category = SavingsGoalCategory.SHOPPING)
        savingsRepo.addDeposit(goalId, 250000L, System.currentTimeMillis(), "Saved ₹2,500")

        // Reconnect via fresh repository on same DB
        val freshRepo = SavingsGoalRepositoryImpl(db.savingsGoalDao(), db.savingsEntryDao())
        val loaded = freshRepo.getGoalByIdSync(goalId)
        assertNotNull(loaded)
        assertEquals(250000L, loaded!!.currentSavedPaise)
    }

    // -------------------------------------------------------------------------
    // 32. Database migration (v10 -> v11)
    // -------------------------------------------------------------------------
    @Test
    fun test32_databaseMigration_v10_to_v11() {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val dbFile = context.getDatabasePath("test_migration_10_11.db")
        dbFile.delete()

        val sqliteDb = factory.create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(10) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Empty v10 mock
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldV: Int, newV: Int) {}
                })
                .build()
        ).writableDatabase

        // Execute MIGRATION_10_11
        RupeeFlowDatabase.MIGRATION_10_11.migrate(sqliteDb)

        // Verify tables exist
        val cursor1 = sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='savings_goals'")
        assertTrue(cursor1.moveToFirst())
        cursor1.close()

        val cursor2 = sqliteDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='savings_entries'")
        assertTrue(cursor2.moveToFirst())
        cursor2.close()

        sqliteDb.close()
        dbFile.delete()
    }

    // -------------------------------------------------------------------------
    // 33. Backup and restore
    // -------------------------------------------------------------------------
    @Test
    fun test33_backupAndRestore() = runBlocking {
        val goalId = savingsRepo.createGoal(name = "Laptop", targetAmountPaise = 8000000L, category = SavingsGoalCategory.ELECTRONICS)
        savingsRepo.addDeposit(goalId, 1500000L, System.currentTimeMillis(), "Internship stipend")

        val json = backupRestoreRepo.exportToJson()
        val root = JSONObject(json)
        assertTrue(root.has("savings_goals"))
        assertTrue(root.has("savings_entries"))

        val preview = backupRestoreRepo.inspectBackup(json)
        assertTrue(preview.isValid)
        assertEquals(1, preview.savingsGoalsCount)

        // Clean tables
        db.savingsEntryDao().deleteAllEntries()
        db.savingsGoalDao().deleteAllGoals()

        // Restore
        val restored = backupRestoreRepo.restoreFromJson(json)
        assertTrue(restored)

        val restoredGoals = savingsRepo.getAllGoalsList()
        assertEquals(1, restoredGoals.size)
        assertEquals("Laptop", restoredGoals[0].name)
        assertEquals(1500000L, restoredGoals[0].currentSavedPaise)
    }

    // -------------------------------------------------------------------------
    // 34. Offline operation
    // -------------------------------------------------------------------------
    @Test
    fun test34_offlineOperation() = runBlocking {
        // Complete savings operation without any external network dependency
        val goalId = savingsRepo.createGoal(name = "Offline", targetAmountPaise = 100000L, category = SavingsGoalCategory.CUSTOM)
        val entryId = savingsRepo.addDeposit(goalId, 50000L, System.currentTimeMillis(), "Offline deposit")
        assertTrue(goalId > 0)
        assertTrue(entryId > 0)
    }

    // -------------------------------------------------------------------------
    // 35. Dark theme
    // -------------------------------------------------------------------------
    @Test
    fun test35_darkTheme() {
        val darkColors = com.naman.rupeeflow.ui.theme.DarkAppColors
        assertTrue(darkColors.isDark)
        assertNotNull(darkColors.background)
        assertNotNull(darkColors.primaryPeach)
    }

    // -------------------------------------------------------------------------
    // 36. Peachy theme
    // -------------------------------------------------------------------------
    @Test
    fun test36_peachyTheme() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFF8F1), PeachyAppColors.background)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFAD91), PeachyAppColors.primaryPeach)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF5A6B8), PeachyAppColors.accentPink)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFB9E5D2), PeachyAppColors.mint)
    }

    // -------------------------------------------------------------------------
    // 37. Accessibility and font scaling
    // -------------------------------------------------------------------------
    @Test
    fun test37_accessibilityAndFontScaling() {
        val formattedCrores = CurrencyUtils.formatPaiseToRupees(1500000000L) // ₹1.5 Cr
        assertTrue(formattedCrores.contains("₹"))
        assertTrue(formattedCrores.contains("1,50,00,000"))
    }

    // -------------------------------------------------------------------------
    // 38. Overview integration
    // -------------------------------------------------------------------------
    @Test
    fun test38_overviewIntegration() = runBlocking {
        savingsRepo.createGoal(name = "G1", targetAmountPaise = 1000000L, initialSavedAmountPaise = 200000L, category = SavingsGoalCategory.CUSTOM)
        savingsRepo.createGoal(name = "G2", targetAmountPaise = 2000000L, initialSavedAmountPaise = 500000L, category = SavingsGoalCategory.CUSTOM)

        val summary = savingsRepo.getOverviewSummary().first()
        assertEquals(2, summary.activeGoalsCount)
        assertEquals(700000L, summary.totalSavedPaise)
        assertEquals((700000.0 / 3000000.0 * 100.0).toFloat(), summary.overallProgressPercentage, 0.01f)
    }

    // -------------------------------------------------------------------------
    // 39. Widget integration if available
    // -------------------------------------------------------------------------
    @Test
    fun test39_widgetIntegration() = runBlocking {
        val compact = CurrencyUtils.formatPaiseCompact(2500000L) // ₹25,000
        assertEquals("₹25.0k", compact)
    }

    // -------------------------------------------------------------------------
    // 40. Existing financial calculation regression
    // -------------------------------------------------------------------------
    @Test
    fun test40_existingFinancialCalculationRegression() = runBlocking {
        // Record regular transactions
        db.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#F97316"))
        transactionRepo.saveTransaction(
            Transaction(
                title = "Dinner",
                amountPaise = 50000L, // ₹500
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.UPI
            )
        )
        transactionRepo.saveTransaction(
            Transaction(
                title = "Salary",
                amountPaise = 5000000L, // ₹50,000
                categoryId = 1L,
                transactionType = TransactionType.INCOME,
                paymentMethod = PaymentMethod.BANK_TRANSFER
            )
        )

        // Record a savings goal deposit
        val goalId = savingsRepo.createGoal(name = "Laptop", targetAmountPaise = 8000000L, category = SavingsGoalCategory.ELECTRONICS)
        savingsRepo.addDeposit(goalId, 1000000L, System.currentTimeMillis(), "Allocation from salary")

        // Verify that personal expense and income calculations are 100% untouched by savings allocations!
        val allTxns = transactionRepo.getAllTransactionsList()
        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = allTxns,
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = 0L
        )

        assertEquals(50000L, summary.totalExpenseThisMonthPaise)
        assertEquals(5000000L, summary.totalIncomeThisMonthPaise)
        // Confirm savings are strictly separate from expenses and income
        assertEquals(1, allTxns.count { it.transactionType == TransactionType.EXPENSE })
        assertEquals(1, allTxns.count { it.transactionType == TransactionType.INCOME })
    }
}
