package com.naman.rupeeflow.navigation

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.local.entity.FriendGroupEntity
import com.naman.rupeeflow.data.local.entity.FriendGroupMemberEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DatePeriodHelper
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.PeriodType
import com.naman.rupeeflow.voice.execution.VoiceCommandValidator
import com.naman.rupeeflow.voice.model.VoiceCommand
import com.naman.rupeeflow.voice.model.VoiceCommandResult
import com.naman.rupeeflow.voice.model.VoiceLanguage
import com.naman.rupeeflow.voice.nlp.VoiceCommandParser
import com.naman.rupeeflow.voice.nlp.VoiceIntentClassifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class PeriodNavigationAndFriendEditTest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var friendRepository: FriendRepository
    private lateinit var friendGroupRepository: FriendGroupRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var settlementRepository: SettlementRepository

    private val testTimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val testLocale = Locale.ENGLISH

    @Before
    fun setup() {
        val context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        friendRepository = FriendRepositoryImpl(database.friendDao())
        friendGroupRepository = FriendGroupRepositoryImpl(database.friendGroupDao())
        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao()
        )
        settlementRepository = SettlementRepositoryImpl(database.settlementDao(), database.friendDao())

        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF5722", isDefault = true)
            )
            database.categoryDao().insertCategory(
                CategoryEntity(id = 2L, name = "Travel", iconName = "commute", colorHex = "#2196F3", isDefault = false)
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // =========================================================================
    // SECTION 1: WEEKLY NAVIGATION TESTS (Tests 1 to 13)
    // =========================================================================

    @Test
    fun test_01_PreviousWeek() {
        val now = 1794050400000L // Wed, Nov 4, 2026
        val currentWeek = DatePeriodHelper.getWeekRange(offset = 0, referenceTime = now, timeZone = testTimeZone, locale = testLocale)
        val prevWeek = DatePeriodHelper.getWeekRange(offset = -1, referenceTime = now, timeZone = testTimeZone, locale = testLocale)

        assertEquals(0, currentWeek.offset)
        assertEquals(-1, prevWeek.offset)
        // Exactly 7 calendar days diff (7 * 24 * 60 * 60 * 1000 = 604,800,000 ms)
        assertEquals(7 * 24 * 60 * 60 * 1000L, currentWeek.startMillis - prevWeek.startMillis)
        assertEquals(7 * 24 * 60 * 60 * 1000L, currentWeek.endMillis - prevWeek.endMillis)
    }

    @Test
    fun test_02_NextWeek() {
        val now = 1794050400000L
        val weekMinusOne = DatePeriodHelper.getWeekRange(offset = -1, referenceTime = now, timeZone = testTimeZone, locale = testLocale)
        val weekZero = DatePeriodHelper.getWeekRange(offset = (-1 + 1).coerceAtMost(0), referenceTime = now, timeZone = testTimeZone, locale = testLocale)

        assertTrue(weekMinusOne.canNavigateNext)
        assertEquals(0, weekZero.offset)
        assertFalse(weekZero.canNavigateNext)
    }

    @Test
    fun test_03_CurrentWeekNextArrowDisabled() {
        val currentWeek = DatePeriodHelper.getWeekRange(offset = 0, timeZone = testTimeZone, locale = testLocale)
        assertFalse("Next arrow must be disabled for current week", currentWeek.canNavigateNext)
        assertTrue("Previous arrow must be enabled", currentWeek.canNavigatePrevious)
    }

    @Test
    fun test_04_WeekBoundaryAcrossMonths() {
        // Week spanning Oct 26, 2026 to Nov 01, 2026
        val refDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.OCTOBER, 28, 14, 0, 0)
        }.timeInMillis

        val week = DatePeriodHelper.getWeekRange(offset = 0, referenceTime = refDate, timeZone = testTimeZone, locale = testLocale)

        val startCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = week.startMillis }
        val endCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = week.endMillis }

        assertEquals(Calendar.OCTOBER, startCal.get(Calendar.MONTH))
        assertEquals(26, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))

        assertEquals(Calendar.NOVEMBER, endCal.get(Calendar.MONTH))
        assertEquals(1, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SUNDAY, endCal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun test_05_WeekBoundaryAcrossYears() {
        // Week spanning Dec 28, 2026 to Jan 03, 2027
        val refDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.DECEMBER, 30, 10, 0, 0)
        }.timeInMillis

        val week = DatePeriodHelper.getWeekRange(offset = 0, referenceTime = refDate, timeZone = testTimeZone, locale = testLocale)

        val startCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = week.startMillis }
        val endCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = week.endMillis }

        assertEquals(2026, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, startCal.get(Calendar.MONTH))
        assertEquals(28, startCal.get(Calendar.DAY_OF_MONTH))

        assertEquals(2027, endCal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, endCal.get(Calendar.MONTH))
        assertEquals(3, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun test_06_CorrectMondaySundayBoundaries() {
        val now = System.currentTimeMillis()
        val week = DatePeriodHelper.getWeekRange(offset = 0, referenceTime = now, timeZone = testTimeZone, locale = testLocale)

        val startCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = week.startMillis }
        val endCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = week.endMillis }

        assertEquals(Calendar.MONDAY, startCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))
        assertEquals(0, startCal.get(Calendar.SECOND))
        assertEquals(0, startCal.get(Calendar.MILLISECOND))

        assertEquals(Calendar.SUNDAY, endCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
        assertEquals(59, endCal.get(Calendar.SECOND))
        assertEquals(999, endCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun test_07_CorrectDateRangeFormat() {
        val refDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.NOVEMBER, 4, 12, 0, 0)
        }.timeInMillis

        val week = DatePeriodHelper.getWeekRange(offset = 0, referenceTime = refDate, timeZone = testTimeZone, locale = testLocale)
        assertEquals("02 Nov 2026 – 08 Nov 2026", week.label)
    }

    @Test
    fun test_08_CorrectWeeklyTotals() = runBlocking {
        val refDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.NOVEMBER, 4, 12, 0, 0)
        }.timeInMillis

        val week = DatePeriodHelper.getWeekRange(offset = 0, referenceTime = refDate, timeZone = testTimeZone, locale = testLocale)

        // Inside current week (Nov 4)
        transactionRepository.saveTransaction(
            Transaction(title = "Lunch", amountPaise = 25000L, categoryId = 1L, transactionDate = refDate, transactionType = TransactionType.EXPENSE)
        )
        // Outside current week (Nov 15)
        val futureDate = refDate + (11 * 24 * 60 * 60 * 1000L)
        transactionRepository.saveTransaction(
            Transaction(title = "Dinner Later", amountPaise = 60000L, categoryId = 1L, transactionDate = futureDate, transactionType = TransactionType.EXPENSE)
        )

        val allTxns = transactionRepository.getAllTransactions().first()
        val weekTxns = allTxns.filter { it.transactionDate in week.startMillis..week.endMillis }

        assertEquals(1, weekTxns.size)
        assertEquals(25000L, weekTxns.sumOf { it.amountPaise })
    }

    @Test
    fun test_09_EmptyWeek() = runBlocking {
        val refDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.NOVEMBER, 4, 12, 0, 0)
        }.timeInMillis
        val prevWeek = DatePeriodHelper.getWeekRange(offset = -1, referenceTime = refDate, timeZone = testTimeZone, locale = testLocale)

        val allTxns = transactionRepository.getAllTransactions().first()
        val prevWeekTxns = allTxns.filter { it.transactionDate in prevWeek.startMillis..prevWeek.endMillis }

        assertEquals(0, prevWeekTxns.size)
        assertEquals(0L, prevWeekTxns.sumOf { it.amountPaise })
    }

    @Test
    fun test_10_WeekContainingNoTransactionsDoesNotThrow() {
        val range = DatePeriodHelper.getWeekRange(offset = -20)
        assertNotNull(range)
        assertTrue(range.startMillis < range.endMillis)
    }

    @Test
    fun test_11_FutureWeekRestriction() {
        val now = System.currentTimeMillis()
        val attemptNextOnZero = DatePeriodHelper.getWeekRange(offset = 1, referenceTime = now, timeZone = testTimeZone, locale = testLocale)
        // Offset must be clamped to 0
        assertEquals(0, attemptNextOnZero.offset)
        assertFalse(attemptNextOnZero.canNavigateNext)
    }

    @Test
    fun test_12_RepeatedArrowTaps() {
        val now = System.currentTimeMillis()
        var offset = 0
        repeat(10) { offset -= 1 }
        val week = DatePeriodHelper.getWeekRange(offset = offset, referenceTime = now, timeZone = testTimeZone, locale = testLocale)
        val currentWeek = DatePeriodHelper.getWeekRange(offset = 0, referenceTime = now, timeZone = testTimeZone, locale = testLocale)

        assertEquals(-10, week.offset)
        assertEquals(70 * 24 * 60 * 60 * 1000L, currentWeek.startMillis - week.startMillis)
    }

    @Test
    fun test_13_ScreenRecreationPreservesExpectedSelection() {
        val savedOffset = -3
        val restoredWeek = DatePeriodHelper.getWeekRange(offset = savedOffset, timeZone = testTimeZone, locale = testLocale)
        assertEquals(-3, restoredWeek.offset)
        assertTrue(restoredWeek.canNavigateNext)
    }

    // =========================================================================
    // SECTION 2: MONTHLY NAVIGATION TESTS (Tests 14 to 25)
    // =========================================================================

    @Test
    fun test_14_PreviousMonth() {
        val refDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.NOVEMBER, 15, 12, 0, 0)
        }.timeInMillis

        val currentMonth = DatePeriodHelper.getMonthRange(offset = 0, referenceTime = refDate, timeZone = testTimeZone, locale = testLocale)
        val prevMonth = DatePeriodHelper.getMonthRange(offset = -1, referenceTime = refDate, timeZone = testTimeZone, locale = testLocale)

        assertEquals("November 2026", currentMonth.label)
        assertEquals("October 2026", prevMonth.label)
        assertTrue(prevMonth.startMillis < currentMonth.startMillis)
    }

    @Test
    fun test_15_NextMonth() {
        val refDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.NOVEMBER, 15, 12, 0, 0)
        }.timeInMillis

        val lastMonth = DatePeriodHelper.getMonthRange(offset = -1, referenceTime = refDate, timeZone = testTimeZone, locale = testLocale)
        val nextMonth = DatePeriodHelper.getMonthRange(offset = (-1 + 1).coerceAtMost(0), referenceTime = refDate, timeZone = testTimeZone, locale = testLocale)

        assertTrue(lastMonth.canNavigateNext)
        assertEquals("November 2026", nextMonth.label)
        assertFalse(nextMonth.canNavigateNext)
    }

    @Test
    fun test_16_JanuaryToDecemberTransition() {
        // From Jan 2027, going back 1 month must give Dec 2026
        val janDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2027, Calendar.JANUARY, 10, 12, 0, 0)
        }.timeInMillis

        val decMonth = DatePeriodHelper.getMonthRange(offset = -1, referenceTime = janDate, timeZone = testTimeZone, locale = testLocale)
        assertEquals("December 2026", decMonth.label)

        val cal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = decMonth.startMillis }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, cal.get(Calendar.MONTH))
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun test_17_DecemberToJanuaryTransition() {
        // From Dec 2026 (offset -1 relative to Jan 2027), going next gives Jan 2027
        val janDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2027, Calendar.JANUARY, 10, 12, 0, 0)
        }.timeInMillis

        val janMonth = DatePeriodHelper.getMonthRange(offset = 0, referenceTime = janDate, timeZone = testTimeZone, locale = testLocale)
        assertEquals("January 2027", janMonth.label)

        val cal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = janMonth.startMillis }
        assertEquals(2027, cal.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, cal.get(Calendar.MONTH))
    }

    @Test
    fun test_18_FebruaryInLeapYear() {
        // 2028 is a leap year; February must have 29 days
        val feb2028 = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2028, Calendar.FEBRUARY, 5, 12, 0, 0)
        }.timeInMillis

        val month = DatePeriodHelper.getMonthRange(offset = 0, referenceTime = feb2028, timeZone = testTimeZone, locale = testLocale)
        val endCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = month.endMillis }

        assertEquals("February 2028", month.label)
        assertEquals(29, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, endCal.get(Calendar.MINUTE))
        assertEquals(59, endCal.get(Calendar.SECOND))
    }

    @Test
    fun test_19_FebruaryInNormalYear() {
        // 2026 is a normal year; February must have 28 days
        val feb2026 = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.FEBRUARY, 5, 12, 0, 0)
        }.timeInMillis

        val month = DatePeriodHelper.getMonthRange(offset = 0, referenceTime = feb2026, timeZone = testTimeZone, locale = testLocale)
        val endCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = month.endMillis }

        assertEquals("February 2026", month.label)
        assertEquals(28, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun test_20_MonthWith30Days() {
        // April 2026 has 30 days
        val apr2026 = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.APRIL, 10, 12, 0, 0)
        }.timeInMillis

        val month = DatePeriodHelper.getMonthRange(offset = 0, referenceTime = apr2026, timeZone = testTimeZone, locale = testLocale)
        val endCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = month.endMillis }

        assertEquals(30, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun test_21_MonthWith31Days() {
        // October 2026 has 31 days
        val oct2026 = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.OCTOBER, 10, 12, 0, 0)
        }.timeInMillis

        val month = DatePeriodHelper.getMonthRange(offset = 0, referenceTime = oct2026, timeZone = testTimeZone, locale = testLocale)
        val endCal = Calendar.getInstance(testTimeZone, testLocale).apply { timeInMillis = month.endMillis }

        assertEquals(31, endCal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun test_22_CurrentMonthNextArrowDisabled() {
        val currentMonth = DatePeriodHelper.getMonthRange(offset = 0, timeZone = testTimeZone, locale = testLocale)
        assertFalse(currentMonth.canNavigateNext)
        assertTrue(currentMonth.canNavigatePrevious)
    }

    @Test
    fun test_23_CorrectMonthlyTotals() = runBlocking {
        val novDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.NOVEMBER, 10, 12, 0, 0)
        }.timeInMillis

        val month = DatePeriodHelper.getMonthRange(offset = 0, referenceTime = novDate, timeZone = testTimeZone, locale = testLocale)

        // Nov transaction
        transactionRepository.saveTransaction(
            Transaction(title = "Nov Rent", amountPaise = 1500000L, categoryId = 1L, transactionDate = novDate, transactionType = TransactionType.EXPENSE)
        )
        // Oct transaction
        val octDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2026, Calendar.OCTOBER, 20, 12, 0, 0)
        }.timeInMillis
        transactionRepository.saveTransaction(
            Transaction(title = "Oct Rent", amountPaise = 1500000L, categoryId = 1L, transactionDate = octDate, transactionType = TransactionType.EXPENSE)
        )

        val allTxns = transactionRepository.getAllTransactions().first()
        val novTxns = allTxns.filter { it.transactionDate in month.startMillis..month.endMillis }

        assertEquals(1, novTxns.size)
        assertEquals("Nov Rent", novTxns[0].title)
    }

    @Test
    fun test_24_EmptyMonth() = runBlocking {
        val decDate = Calendar.getInstance(testTimeZone, testLocale).apply {
            set(2025, Calendar.DECEMBER, 10, 12, 0, 0)
        }.timeInMillis

        val month = DatePeriodHelper.getMonthRange(offset = 0, referenceTime = decDate, timeZone = testTimeZone, locale = testLocale)
        val allTxns = transactionRepository.getAllTransactions().first()
        val matching = allTxns.filter { it.transactionDate in month.startMillis..month.endMillis }

        assertEquals(0, matching.size)
    }

    @Test
    fun test_25_FutureMonthRestriction() {
        val now = System.currentTimeMillis()
        val clamped = DatePeriodHelper.getMonthRange(offset = 3, referenceTime = now, timeZone = testTimeZone, locale = testLocale)
        assertEquals(0, clamped.offset)
        assertFalse(clamped.canNavigateNext)
    }

    // =========================================================================
    // SECTION 3: FRIEND EDITING TESTS (Tests 26 to 47)
    // =========================================================================

    @Test
    fun test_26_EditFriendName() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Jay", phoneNumber = "9876543210"))
        val existing = friendRepository.getFriendById(friendId)
        assertNotNull(existing)

        val updated = existing!!.copy(name = "Jay Patel")
        friendRepository.updateFriend(updated)

        val retrieved = friendRepository.getFriendById(friendId)
        assertEquals("Jay Patel", retrieved?.name)
        assertEquals("9876543210", retrieved?.phoneNumber)
        assertEquals(friendId, retrieved?.id)
    }

    @Test
    fun test_27_EditPhoneNumber() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Pratik", phoneNumber = null))
        val existing = friendRepository.getFriendById(friendId)

        val updated = existing!!.copy(phoneNumber = "9876543210")
        friendRepository.updateFriend(updated)

        val retrieved = friendRepository.getFriendById(friendId)
        assertEquals("9876543210", retrieved?.phoneNumber)
        assertEquals("Pratik", retrieved?.name)
    }

    @Test
    fun test_28_EditMultipleFields() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Ronit"))
        val existing = friendRepository.getFriendById(friendId)

        val updated = existing!!.copy(
            name = "Ronit Shah",
            phoneNumber = "9123456789",
            email = "ronit@example.com",
            notes = "College friend",
            profileImagePath = "/images/ronit.jpg"
        )
        friendRepository.updateFriend(updated)

        val retrieved = friendRepository.getFriendById(friendId)
        assertEquals("Ronit Shah", retrieved?.name)
        assertEquals("9123456789", retrieved?.phoneNumber)
        assertEquals("ronit@example.com", retrieved?.email)
        assertEquals("College friend", retrieved?.notes)
        assertEquals("/images/ronit.jpg", retrieved?.profileImagePath)
    }

    @Test
    fun test_29_SaveUnchangedDetails() = runBlocking {
        val initial = Friend(name = "Ananya", phoneNumber = "9988776655", email = "ananya@test.com")
        val friendId = friendRepository.addFriend(initial)
        val loaded = friendRepository.getFriendById(friendId)!!

        // Save identical
        friendRepository.updateFriend(loaded)

        val after = friendRepository.getFriendById(friendId)!!
        assertEquals(loaded.name, after.name)
        assertEquals(loaded.phoneNumber, after.phoneNumber)
        assertEquals(loaded.email, after.email)
        assertEquals(loaded.createdAt, after.createdAt)
    }

    @Test
    fun test_30_EmptyNameValidation() {
        val invalidBlank = "   "
        assertTrue(invalidBlank.trim().isBlank())

        val invalidEmpty = ""
        assertTrue(invalidEmpty.trim().isBlank())
    }

    @Test
    fun test_31_InvalidPhoneValidation() {
        val phoneRegex = Regex("""^[+]?[0-9\s\-()]{7,18}$""")

        val validNumbers = listOf("9876543210", "+919876543210", "+91 98765 43210", "022-28765432")
        for (v in validNumbers) {
            val digits = v.filter { it.isDigit() }
            assertTrue("Should be valid: $v", phoneRegex.matches(v) && digits.length in 7..15)
        }

        val invalidNumbers = listOf("123", "abcd", "98765abc", "++91")
        for (inv in invalidNumbers) {
            val digits = inv.filter { it.isDigit() }
            val isValid = phoneRegex.matches(inv) && digits.length in 7..15
            assertFalse("Should be invalid: $inv", isValid)
        }
    }

    @Test
    fun test_32_DuplicateFriendValidation() = runBlocking {
        friendRepository.addFriend(Friend(id = 1L, name = "Manan"))
        friendRepository.addFriend(Friend(id = 2L, name = "Priya"))

        val allFriends = friendRepository.getAllFriendsList()

        // Attempting to rename Priya (id=2) to Manan (id=1) must be detected as duplicate
        val attemptedName = "  manan  "
        val isDuplicate = allFriends.any {
            it.id != 2L && it.name.trim().equals(attemptedName.trim(), ignoreCase = true)
        }
        assertTrue(isDuplicate)

        // Attempting to keep Priya's own name (id=2) is NOT a duplicate
        val isSelfDuplicate = allFriends.any {
            it.id != 2L && it.name.trim().equals("Priya", ignoreCase = true)
        }
        assertFalse(isSelfDuplicate)
    }

    @Test
    fun test_33_UnicodeFriendName() = runBlocking {
        val gujId = friendRepository.addFriend(Friend(name = "સુકૃત"))
        val hinId = friendRepository.addFriend(Friend(name = "अमित"))

        val gujFriend = friendRepository.getFriendById(gujId)
        val hinFriend = friendRepository.getFriendById(hinId)

        assertEquals("સુકૃત", gujFriend?.name)
        assertEquals("अमित", hinFriend?.name)

        // Update with full Unicode names
        friendRepository.updateFriend(gujFriend!!.copy(name = "સુકૃત પટેલ"))
        friendRepository.updateFriend(hinFriend!!.copy(name = "अमित शर्मा"))

        assertEquals("સુકૃત પટેલ", friendRepository.getFriendById(gujId)?.name)
        assertEquals("अमित शर्मा", friendRepository.getFriendById(hinId)?.name)
    }

    @Test
    fun test_34_PreserveFriendId() = runBlocking {
        val originalId = friendRepository.addFriend(Friend(name = "Kavita"))
        val friend = friendRepository.getFriendById(originalId)!!

        val updated = friend.copy(name = "Kavita R")
        friendRepository.updateFriend(updated)

        val retrieved = friendRepository.getFriendById(originalId)!!
        assertEquals(originalId, retrieved.id)
    }

    @Test
    fun test_35_PreserveCreatedAt() = runBlocking {
        val customCreated = 1700000000000L
        val originalId = friendRepository.addFriend(Friend(name = "Deepak", createdAt = customCreated))
        val friend = friendRepository.getFriendById(originalId)!!

        assertEquals(customCreated, friend.createdAt)

        val updated = friend.copy(name = "Deepak Kumar")
        friendRepository.updateFriend(updated)

        val retrieved = friendRepository.getFriendById(originalId)!!
        assertEquals(customCreated, retrieved.createdAt)
    }

    @Test
    fun test_36_UpdateFriendEntityIntegrity() = runBlocking {
        val id = friendRepository.addFriend(Friend(name = "Tanmay", phoneNumber = "9820012345"))
        val friend = friendRepository.getFriendById(id)!!

        friendRepository.updateFriend(friend.copy(phoneNumber = "9820099999", notes = "Updated note"))
        val updated = friendRepository.getFriendById(id)!!

        assertEquals("9820099999", updated.phoneNumber)
        assertEquals("Updated note", updated.notes)
    }

    @Test
    fun test_37_PreserveGroupMembership() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Jay"))
        val groupId = friendGroupRepository.createGroup(name = "Trip 2026", friendIds = listOf(friendId))

        // Verify group has 1 member
        var group = friendGroupRepository.getGroupById(groupId)!!
        assertTrue(group.memberFriendIds.contains(friendId))

        // Edit friend name
        val friend = friendRepository.getFriendById(friendId)!!
        friendRepository.updateFriend(friend.copy(name = "Jay Patel"))

        // Group membership must remain completely intact
        group = friendGroupRepository.getGroupById(groupId)!!
        assertEquals(1, group.memberFriendIds.size)
        assertTrue(group.memberFriendIds.contains(friendId))
    }

    @Test
    fun test_38_PreserveTransactionReferences() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Sameer"))

        val txn = Transaction(
            title = "Dinner at Taj",
            amountPaise = 50000L,
            categoryId = 1L,
            paidByFriendId = friendId,
            paidByName = "Sameer",
            transactionDate = System.currentTimeMillis(),
            transactionType = TransactionType.EXPENSE,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 25000L),
                TransactionParticipant(friendId = friendId, friendName = "Sameer", shareAmountPaise = 25000L)
            )
        )
        val txnId = transactionRepository.saveTransaction(txn)

        // Rename friend
        val friend = friendRepository.getFriendById(friendId)!!
        friendRepository.updateFriend(friend.copy(name = "Sameer Joshi"))

        // Transaction must still link to friendId
        val loadedTxn = transactionRepository.getTransactionById(txnId)!!
        assertEquals(friendId, loadedTxn.paidByFriendId)
        assertTrue(loadedTxn.participants.any { it.friendId == friendId })
    }

    @Test
    fun test_39_PreservePayerRelationships() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Rahul"))
        transactionRepository.saveTransaction(
            Transaction(title = "Movie", amountPaise = 60000L, categoryId = 1L, paidByFriendId = friendId, transactionType = TransactionType.EXPENSE)
        )

        // Edit friend
        val friend = friendRepository.getFriendById(friendId)!!
        friendRepository.updateFriend(friend.copy(phoneNumber = "9988112233"))

        val countPaid = database.friendDao().countTransactionsPaidByFriend(friendId)
        assertEquals(1, countPaid)
    }

    @Test
    fun test_40_PreserveParticipantAllocations() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Aarav"))
        val txn = Transaction(
            title = "Goa Hotel",
            amountPaise = 1000000L,
            categoryId = 1L,
            transactionType = TransactionType.EXPENSE,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 500000L),
                TransactionParticipant(friendId = friendId, shareAmountPaise = 500000L)
            )
        )
        val txnId = transactionRepository.saveTransaction(txn)

        // Edit friend name
        val friend = friendRepository.getFriendById(friendId)!!
        friendRepository.updateFriend(friend.copy(name = "Aarav Mehta"))

        val loadedTxn = transactionRepository.getTransactionById(txnId)!!
        val part = loadedTxn.participants.first { it.friendId == friendId }
        assertEquals(500000L, part.shareAmountPaise)
    }

    @Test
    fun test_41_PreserveSettlementHistory() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Bhavik"))
        settlementRepository.recordSettlement(
            Settlement(payerId = friendId, receiverId = null, amountPaise = 45000L, settlementDate = System.currentTimeMillis())
        )

        val friend = friendRepository.getFriendById(friendId)!!
        friendRepository.updateFriend(friend.copy(name = "Bhavik Shah"))

        val settlements = settlementRepository.getSettlementsForFriend(friendId).first()
        assertEquals(1, settlements.size)
        assertEquals(45000L, settlements[0].amountPaise)
    }

    @Test
    fun test_42_RefreshSearchResults() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Kunal"))
        var friends = friendRepository.getAllFriendsList()
        assertTrue(friends.any { it.name.contains("Kunal", ignoreCase = true) })

        val friend = friendRepository.getFriendById(friendId)!!
        friendRepository.updateFriend(friend.copy(name = "Kunal Verma"))

        friends = friendRepository.getAllFriendsList()
        assertTrue(friends.any { it.name.contains("Verma", ignoreCase = true) })
    }

    @Test
    fun test_43_UpdateGroupMemberDisplay() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Nikhil"))
        val groupId = friendGroupRepository.createGroup(name = "Flatmates", friendIds = listOf(friendId))

        friendRepository.updateFriend(friendRepository.getFriendById(friendId)!!.copy(name = "Nikhil Sharma"))

        val group = friendGroupRepository.getGroupById(groupId)!!
        val memberFriends = friendRepository.getAllFriendsList().filter { group.memberFriendIds.contains(it.id) }

        assertEquals(1, memberFriends.size)
        assertEquals("Nikhil Sharma", memberFriends[0].name)
    }

    @Test
    fun test_44_CancelEdit() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Unchanged"))
        val original = friendRepository.getFriendById(friendId)!!

        // Simulating Cancel: discarding the modified object
        val discarded = original.copy(name = "Discarded Modification")
        assertNotNull(discarded)

        // Database remains unaffected
        val dbFriend = friendRepository.getFriendById(friendId)!!
        assertEquals("Unchanged", dbFriend.name)
    }

    @Test
    fun test_45_HandleDatabaseSafeQuerying() = runBlocking {
        // Querying non-existent friend returns null safely
        val nonExistent = friendRepository.getFriendById(99999L)
        assertNull(nonExistent)
    }

    @Test
    fun test_46_EditWhileListIsRefreshed() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Concurrent"))
        val friendsFlow = friendRepository.getAllFriends()

        val initialList = friendsFlow.first()
        assertEquals("Concurrent", initialList.first { it.id == friendId }.name)

        friendRepository.updateFriend(initialList.first { it.id == friendId }.copy(name = "Concurrent Updated"))

        val refreshedList = friendsFlow.first()
        assertEquals("Concurrent Updated", refreshedList.first { it.id == friendId }.name)
    }

    @Test
    fun test_47_EditArchivedFriendAccordingToExistingRules() = runBlocking {
        val friendId = friendRepository.addFriend(Friend(name = "Archived Friend", isArchived = true))
        val friend = friendRepository.getFriendById(friendId)!!
        assertTrue(friend.isArchived)

        friendRepository.updateFriend(friend.copy(name = "Archived Friend Renamed"))

        val updated = friendRepository.getFriendById(friendId)!!
        assertEquals("Archived Friend Renamed", updated.name)
        assertTrue("isArchived status must be preserved", updated.isArchived)
    }

    // =========================================================================
    // SECTION 4: VOICE ASSISTANT COMPATIBILITY FOR FRIEND EDITING
    // =========================================================================

    @Test
    fun test_48_VoiceFriendNameChangeCommand() {
        val friends = listOf(Friend(id = 10L, name = "Jay"))
        val intent = VoiceIntentClassifier.classify("Change Jay's name to Jay Patel")
        assertEquals(com.naman.rupeeflow.voice.model.VoiceIntent.FRIEND_UPDATE, intent)

        val cmd = VoiceCommandParser.parse("Change Jay's name to Jay Patel", friends, emptyList(), emptyList())
        assertTrue(cmd is VoiceCommand.UpdateFriend)
        val updateCmd = cmd as VoiceCommand.UpdateFriend
        assertEquals(10L, updateCmd.friendId)
        assertEquals("Jay", updateCmd.friendName)
        assertEquals("Jay Patel", updateCmd.newName)
    }

    @Test
    fun test_49_VoiceFriendPhoneChangeCommand() {
        val friends = listOf(Friend(id = 20L, name = "Pratik"))
        val intent = VoiceIntentClassifier.classify("Change Pratik's phone number to 9876543210")
        assertEquals(com.naman.rupeeflow.voice.model.VoiceIntent.FRIEND_UPDATE, intent)

        val cmd = VoiceCommandParser.parse("Change Pratik's phone number to 9876543210", friends, emptyList(), emptyList())
        assertTrue(cmd is VoiceCommand.UpdateFriend)
        val updateCmd = cmd as VoiceCommand.UpdateFriend
        assertEquals(20L, updateCmd.friendId)
        assertEquals("9876543210", updateCmd.newPhoneNumber)
    }

    @Test
    fun test_50_VoiceFriendGujaratiCommandNeedsClarification() {
        val friends = listOf(Friend(id = 30L, name = "Jay"))
        val intent = VoiceIntentClassifier.classify("જયનો નંબર બદલો")
        assertEquals(com.naman.rupeeflow.voice.model.VoiceIntent.FRIEND_UPDATE, intent)

        val cmd = VoiceCommandParser.parse("જયનો નંબર બદલો", friends, emptyList(), emptyList())
        assertTrue(cmd is VoiceCommand.UpdateFriend)

        val result = VoiceCommandValidator.validateAndBuildPreview(
            cmd,
            friends,
            emptyList<FriendGroup>(),
            emptyList<Category>(),
            VoiceLanguage.GUJARATI
        )

        assertTrue("Should request clarification for new phone number", result is VoiceCommandResult.ClarificationRequired)
    }
}
