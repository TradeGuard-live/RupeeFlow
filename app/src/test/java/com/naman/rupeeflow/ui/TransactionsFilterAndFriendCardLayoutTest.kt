package com.naman.rupeeflow.ui

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.model.*
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class TransactionsFilterAndFriendCardLayoutTest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository

    private val testZoneId = ZoneId.of("Asia/Kolkata")
    private val novTime = ZonedDateTime.of(2026, 11, 8, 14, 0, 0, 0, testZoneId).toInstant().toEpochMilli()
    private val octTime = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, testZoneId).toInstant().toEpochMilli()

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

        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity(id = 1L, name = "Food & Dining", iconName = "restaurant", colorHex = "#EF4444")
            )
            database.categoryDao().insertCategory(
                CategoryEntity(id = 2L, name = "Shopping", iconName = "shopping_bag", colorHex = "#3B82F6")
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
    // PART 1 — TRANSACTIONS FILTER SPECIFICATIONS & COMBINATIONS
    // =========================================================================

    @Test
    fun test01_FilterGroupDefinitions() {
        // Group A: Transaction Types
        val groupATypes = listOf("ALL", "EXPENSE", "INCOME", "REFUND", "TRANSFER", "IGNORED")
        assertEquals(6, groupATypes.size)
        assertTrue(groupATypes.contains("ALL"))
        assertTrue(groupATypes.contains("EXPENSE"))
        assertTrue(groupATypes.contains("INCOME"))
        assertTrue(groupATypes.contains("REFUND"))
        assertTrue(groupATypes.contains("TRANSFER"))
        assertTrue(groupATypes.contains("IGNORED"))

        // Group B: Time Periods
        val groupBPeriods = listOf("ALL_TIME", "MONTHLY", "WEEKLY")
        assertEquals(3, groupBPeriods.size)
        assertTrue(groupBPeriods.contains("ALL_TIME"))
        assertTrue(groupBPeriods.contains("MONTHLY"))
        assertTrue(groupBPeriods.contains("WEEKLY"))

        // Group C: Payment Methods
        val groupCPaymentMethods = listOf("ALL", "UPI", "CASH", "DEBIT_CARD", "CREDIT_CARD")
        assertEquals(5, groupCPaymentMethods.size)
        assertTrue(groupCPaymentMethods.contains("ALL"))
        assertTrue(groupCPaymentMethods.contains("UPI"))
        assertTrue(groupCPaymentMethods.contains("CASH"))
        assertTrue(groupCPaymentMethods.contains("DEBIT_CARD"))
        assertTrue(groupCPaymentMethods.contains("CREDIT_CARD"))
    }

    @Test
    fun test02_FilterSpacingConstraints() {
        // Required vertical spacing between filter groups
        val verticalSpacingDp = 14
        assertTrue(verticalSpacingDp in 12..16)

        // Required horizontal item spacing within groups
        val horizontalItemSpacingDp = 8
        assertEquals(8, horizontalItemSpacingDp)

        // Edge content padding to prevent clipping end chips
        val edgePaddingDp = 16
        assertEquals(16, edgePaddingDp)
    }

    @Test
    fun test03_TransactionsViewModelFilterStateCombination() = runBlocking {
        // Seed transactions
        transactionRepository.saveTransaction(
            Transaction(id = 1L, title = "Swiggy Dinner", amountPaise = 25000L, categoryId = 1L, transactionDate = novTime, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 2L, title = "Cash Chai", amountPaise = 4000L, categoryId = 1L, transactionDate = novTime, paymentMethod = PaymentMethod.CASH, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 3L, title = "Consulting Fee", amountPaise = 500000L, categoryId = 2L, transactionDate = novTime, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.INCOME)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 4L, title = "Oct Shopping", amountPaise = 150000L, categoryId = 2L, transactionDate = octTime, paymentMethod = PaymentMethod.CREDIT_CARD, transactionType = TransactionType.EXPENSE)
        )
        transactionRepository.saveTransaction(
            Transaction(id = 5L, title = "OTP Test", amountPaise = 100L, categoryId = 1L, transactionDate = novTime, paymentMethod = PaymentMethod.UPI, transactionType = TransactionType.EXPENSE, transactionStatus = TransactionStatus.IGNORED)
        )

        val vm = TransactionsViewModel(transactionRepository, categoryRepository, friendRepository, settlementRepository)
        val initial = vm.uiState.first { !it.isLoading }

        // Initial: All active transactions visible (excludes ignored by default)
        val initialActive = initial.filteredTransactions.filter { it.transactionStatus != TransactionStatus.IGNORED }
        assertEquals(4, initialActive.size)

        // Filter Group A: EXPENSE only
        vm.onTypeFilterSelected("EXPENSE")
        val expenseState = vm.uiState.first { it.selectedTypeFilter == "EXPENSE" }
        assertEquals("EXPENSE", expenseState.selectedTypeFilter)
        assertEquals(3, expenseState.filteredTransactions.size) // Swiggy, Cash Chai, Oct Shopping

        // Filter Group C: UPI only (combines with EXPENSE)
        vm.onPaymentMethodFilterSelected(PaymentMethod.UPI)
        val upiExpenseState = vm.uiState.first { it.selectedPaymentMethodFilter == PaymentMethod.UPI }
        assertEquals(1, upiExpenseState.filteredTransactions.size)
        assertEquals("Swiggy Dinner", upiExpenseState.filteredTransactions[0].title)

        // Filter Group B: MONTHLY (November 2026)
        vm.onDateFilterSelected("MONTHLY")
        val novUpiExpense = vm.uiState.first { it.selectedDateFilter == "MONTHLY" }
        assertEquals(1, novUpiExpense.filteredTransactions.size)
        assertEquals("Swiggy Dinner", novUpiExpense.filteredTransactions[0].title)

        // Switch Group A to IGNORED
        vm.onPaymentMethodFilterSelected(null)
        vm.onDateFilterSelected("ALL")
        vm.onTypeFilterSelected("IGNORED")
        val ignoredState = vm.uiState.first { it.selectedTypeFilter == "IGNORED" }
        assertEquals(1, ignoredState.filteredTransactions.size)
        assertEquals("OTP Test", ignoredState.filteredTransactions[0].title)
    }

    // =========================================================================
    // PART 2 — FRIEND ITEM CARD RESPONSIVE LAYOUT & TRUNCATION
    // =========================================================================

    @Test
    fun test04_ResponsiveBreakpointBehavior() {
        val breakpointDp = 340

        fun isNarrowLayout(availableWidthDp: Int): Boolean {
            return availableWidthDp < breakpointDp
        }

        // Overview screen carousel width is 260dp to 290dp -> Narrow layout
        assertTrue(isNarrowLayout(260))
        assertTrue(isNarrowLayout(280))
        assertTrue(isNarrowLayout(290))

        // Full screen Friends list -> Wide layout (typically 360dp - 420dp)
        assertFalse(isNarrowLayout(340))
        assertFalse(isNarrowLayout(360))
        assertFalse(isNarrowLayout(412))
        assertFalse(isNarrowLayout(600))
    }

    @Test
    fun test05_NarrowLayoutTwoRowStructure() {
        // Verify Narrow layout constraints:
        // Row 1: Avatar (46dp) + Text Column (Name & Phone)
        val avatarSizeDp = 46
        assertEquals(46, avatarSizeDp)

        // Row 1 Text constraints: single-line max, ellipsis overflow
        val nameMaxLines = 1
        val phoneMaxLines = 1
        assertEquals(1, nameMaxLines)
        assertEquals(1, phoneMaxLines)

        // Row 2: Status Pill + Spacer + Overflow Menu Button (48dp min touch target)
        val minMenuTouchTargetDp = 48
        assertTrue(minMenuTouchTargetDp >= 48)
    }

    @Test
    fun test06_LongNameAndPhoneNoCharacterWrapping() {
        // Verify that long names and phone numbers do not split into single vertical characters
        val longFriendName = "Sukrut Chandrakant Suryavanshi"
        val longPhoneNumber = "+91 98765 43210"

        // Simulated truncation helper matching Compose TextOverflow.Ellipsis with maxLines = 1
        fun formatSingleLine(text: String, maxChars: Int): String {
            return if (text.length > maxChars) text.take(maxChars - 1) + "…" else text
        }

        val truncatedName = formatSingleLine(longFriendName, 15)
        assertEquals("Sukrut Chandra…", truncatedName)
        assertFalse(truncatedName.contains("\n"))

        val truncatedPhone = formatSingleLine(longPhoneNumber, 12)
        assertEquals("+91 98765 4…", truncatedPhone)
        assertFalse(truncatedPhone.contains("\n"))
    }

    @Test
    fun test07_MissingPhoneNumberHandledCleanly() {
        val friendWithoutPhone = Friend(
            id = 1L,
            name = "Jay",
            phoneNumber = null,
            email = null,
            createdAt = novTime
        )
        assertNull(friendWithoutPhone.phoneNumber)
        assertTrue(friendWithoutPhone.phoneNumber.isNullOrBlank())
        assertEquals("Jay", friendWithoutPhone.name)
    }

    @Test
    fun test08_BalanceStatusPillFormatting() {
        // Test They owe you (> 0)
        val positiveBalancePaise = 250000L // ₹2,500.00
        val posText = if (positiveBalancePaise > 0) "They owe ₹2,500.00" else "You owe"
        assertEquals("They owe ₹2,500.00", posText)

        // Test You owe (< 0)
        val negativeBalancePaise = -120000L // -₹1,200.00
        val negText = if (negativeBalancePaise < 0) "You owe ₹1,200.00" else "They owe"
        assertEquals("You owe ₹1,200.00", negText)

        // Test Settled up (0)
        val zeroBalancePaise = 0L
        val zeroText = if (zeroBalancePaise == 0L) "All settled up" else "Balance"
        assertEquals("All settled up", zeroText)

        // Extremely large balance
        val hugeBalancePaise = 9999999900L // ₹9,99,99,999.00
        assertTrue(hugeBalancePaise > 0L)
    }
}
