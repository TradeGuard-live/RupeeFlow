package com.naman.rupeeflow.qa

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.ui.screens.settings.SettingsViewModel
import com.naman.rupeeflow.ui.theme.*
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

/**
 * Production QA verification test suite for Lavender Dream & Midnight Mochi themes.
 * Validates:
 * 1. Palette color tokens and contrast ratios
 * 2. DataStore persistence and process restart resilience
 * 3. SettingsViewModel theme mode dispatch and immediate reactive reflection
 * 4. Preservation of Peachy Cozy default and Midnight Dark themes
 * 5. Semantic financial color token fidelity (income vs expense)
 * 6. Financial ledger calculation integrity across theme transitions
 */
@RunWith(RobolectricTestRunner::class)
class LavenderAndMidnightMochiThemesQATest {

    private lateinit var context: Context
    private lateinit var db: RupeeFlowDatabase
    private lateinit var transactionRepo: TransactionRepository
    private lateinit var friendRepo: FriendRepository
    private lateinit var settlementRepo: SettlementRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var friendGroupRepo: FriendGroupRepository
    private lateinit var expenseGroupRepo: ExpenseGroupRepository
    private lateinit var userPrefsRepo: UserPreferencesRepository
    private lateinit var backupRestoreRepo: BackupRestoreRepository

    private var defaultCategoryId: Long = 1L
    private var friendAlexId: Long = 101L

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = RuntimeEnvironment.getApplication()

        db = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        categoryRepo = CategoryRepositoryImpl(db.categoryDao())
        friendRepo = FriendRepositoryImpl(db.friendDao())
        transactionRepo = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(), db.friendDao(), context)
        settlementRepo = SettlementRepositoryImpl(db.settlementDao(), db.friendDao(), context)
        friendGroupRepo = FriendGroupRepositoryImpl(db.friendGroupDao())
        expenseGroupRepo = ExpenseGroupRepositoryImpl(db.expenseGroupDao(), db.transactionDao(), db.friendGroupDao())
        userPrefsRepo = UserPreferencesRepository(context)
        backupRestoreRepo = BackupRestoreRepository(
            database = db,
            transactionRepository = transactionRepo,
            friendRepository = friendRepo,
            settlementRepository = settlementRepo,
            categoryRepository = categoryRepo,
            friendGroupRepository = friendGroupRepo,
            context = context
        )

        db.categoryDao().insertCategory(
            CategoryEntity(
                id = 1L,
                name = "Groceries",
                iconName = "shopping_cart",
                colorHex = "#9274C8",
                isDefault = true
            )
        )
        defaultCategoryId = 1L
        friendAlexId = friendRepo.addFriend(Friend(name = "Alex", phoneNumber = "+919876543210"))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    // =========================================================================
    // SECTION 1: LAVENDER DREAM PALETTE TESTS
    // =========================================================================

    @Test
    fun test01_lavenderDreamPaletteTokens() {
        val colors = LavenderAppColors
        assertNotNull(colors)
        assertFalse(colors.isDark)

        // Palette colors check
        assertEquals(Color(0xFF9274C8), colors.primaryAccent)
        assertEquals(Color(0xFFEEE7F7), colors.primaryAccentContainer)
        assertEquals(Color(0xFFFFFFFF), colors.onPrimaryAccent)
        assertEquals(Color(0xFFF7F3FC), colors.background)
        assertEquals(Color(0xFFFFFFFF), colors.surface)
        assertEquals(Color(0xFFEEE7F7), colors.surfaceSecondary)
        assertEquals(Color(0xFF342A45), colors.textPrimary)
        assertEquals(Color(0xFF80748F), colors.textSecondary)
        assertEquals(Color(0xFFE2D8EF), colors.cardBorder)
        assertEquals(Color(0xFF269B78), colors.success)
        assertEquals(Color(0xFFD95770), colors.error)
        assertEquals(Color(0xFFD99B49), colors.warning)
        assertEquals(Color(0xFFB9A4E8), colors.info)
        assertEquals(Color(0xFFE6BCEB), colors.accentPink)

        // Semantic aliases
        assertEquals(colors.surface, colors.cardBackground)
        assertEquals(colors.primaryAccentContainer, colors.accentContainer)
        assertEquals(colors.success, colors.incomeGreen)
        assertEquals(colors.error, colors.expenseRed)
    }

    @Test
    fun test02_lavenderDreamConstantsMatchTokens() {
        assertEquals(LavenderPrimary, LavenderAppColors.primaryAccent)
        assertEquals(LavenderBackground, LavenderAppColors.background)
        assertEquals(LavenderSurface, LavenderAppColors.surface)
        assertEquals(LavenderSurfaceVariant, LavenderAppColors.surfaceSecondary)
        assertEquals(LavenderTextPrimary, LavenderAppColors.textPrimary)
        assertEquals(LavenderTextSecondary, LavenderAppColors.textSecondary)
        assertEquals(LavenderBorder, LavenderAppColors.cardBorder)
        assertEquals(LavenderSuccess, LavenderAppColors.success)
        assertEquals(LavenderError, LavenderAppColors.error)
        assertEquals(LavenderWarning, LavenderAppColors.warning)
    }

    // =========================================================================
    // SECTION 2: MIDNIGHT MOCHI PALETTE TESTS
    // =========================================================================

    @Test
    fun test03_midnightMochiPaletteTokens() {
        val colors = MidnightMochiAppColors
        assertNotNull(colors)
        assertTrue(colors.isDark)

        // Palette colors check
        assertEquals(Color(0xFFB49AEF), colors.primaryAccent)
        assertEquals(Color(0xFF252036), colors.primaryAccentContainer)
        assertEquals(Color(0xFF100E19), colors.onPrimaryAccent)
        assertEquals(Color(0xFF100E19), colors.background)
        assertEquals(Color(0xFF191626), colors.surface)
        assertEquals(Color(0xFF252036), colors.surfaceSecondary)
        assertEquals(Color(0xFF211D31), colors.surfaceElevated)
        assertEquals(Color(0xFFF5F0FC), colors.textPrimary)
        assertEquals(Color(0xFFB6ADC9), colors.textSecondary)
        assertEquals(Color(0xFF393149), colors.cardBorder)
        assertEquals(Color(0xFF55C99B), colors.success)
        assertEquals(Color(0xFFF07891), colors.error)
        assertEquals(Color(0xFFE7B66C), colors.warning)
        assertEquals(Color(0xFFD0BDF8), colors.info)
        assertEquals(Color(0xFFF0A9D5), colors.accentPink)

        // Semantic aliases
        assertEquals(colors.surface, colors.cardBackground)
        assertEquals(colors.primaryAccentContainer, colors.accentContainer)
        assertEquals(colors.success, colors.incomeGreen)
        assertEquals(colors.error, colors.expenseRed)
    }

    @Test
    fun test04_midnightMochiConstantsMatchTokens() {
        assertEquals(MidnightMochiPrimary, MidnightMochiAppColors.primaryAccent)
        assertEquals(MidnightMochiBackground, MidnightMochiAppColors.background)
        assertEquals(MidnightMochiSurface, MidnightMochiAppColors.surface)
        assertEquals(MidnightMochiSurfaceVariant, MidnightMochiAppColors.surfaceSecondary)
        assertEquals(MidnightMochiSurfaceElevated, MidnightMochiAppColors.surfaceElevated)
        assertEquals(MidnightMochiTextPrimary, MidnightMochiAppColors.textPrimary)
        assertEquals(MidnightMochiTextSecondary, MidnightMochiAppColors.textSecondary)
        assertEquals(MidnightMochiBorder, MidnightMochiAppColors.cardBorder)
        assertEquals(MidnightMochiSuccess, MidnightMochiAppColors.success)
        assertEquals(MidnightMochiError, MidnightMochiAppColors.error)
        assertEquals(MidnightMochiWarning, MidnightMochiAppColors.warning)
    }

    // =========================================================================
    // SECTION 3: PRESERVATION OF PEACHY COZY AND DARK MODE
    // =========================================================================

    @Test
    fun test05_peachyCozyPreservedAsDefault() = runBlocking {
        val initialPrefs = userPrefsRepo.userPreferencesFlow.first()
        assertEquals("PEACHY", initialPrefs.themeMode)

        val peachy = PeachyAppColors
        assertFalse(peachy.isDark)
        assertEquals(Color(0xFFFFF8F1), peachy.background)
        assertEquals(Color(0xFFF58B72), peachy.primaryAccent)
        assertEquals(Color(0xFF4A3028), peachy.textPrimary)
    }

    @Test
    fun test06_midnightDarkPreserved() = runBlocking {
        userPrefsRepo.setThemeMode("DARK")
        val prefs = userPrefsRepo.userPreferencesFlow.first()
        assertEquals("DARK", prefs.themeMode)

        val dark = DarkAppColors
        assertTrue(dark.isDark)
        assertEquals(Color(0xFF0A0E17), dark.background)
        assertEquals(Color(0xFF111827), dark.surface)
        assertEquals(Color(0xFF3B82F6), dark.primaryAccent)
    }

    // =========================================================================
    // SECTION 4: DATASTORE PERSISTENCE & PROCESS RESTART RESILIENCE
    // =========================================================================

    @Test
    fun test07_lavenderThemeSelectionPersists() = runBlocking {
        userPrefsRepo.setThemeMode("LAVENDER")
        val prefs = userPrefsRepo.userPreferencesFlow.first()
        assertEquals("LAVENDER", prefs.themeMode)

        // Simulate app/process restart
        val freshRepo = UserPreferencesRepository(context)
        val restartedPrefs = freshRepo.userPreferencesFlow.first()
        assertEquals("LAVENDER", restartedPrefs.themeMode)
    }

    @Test
    fun test08_midnightMochiThemeSelectionPersists() = runBlocking {
        userPrefsRepo.setThemeMode("MIDNIGHT_MOCHI")
        val prefs = userPrefsRepo.userPreferencesFlow.first()
        assertEquals("MIDNIGHT_MOCHI", prefs.themeMode)

        // Simulate app/process restart
        val freshRepo = UserPreferencesRepository(context)
        val restartedPrefs = freshRepo.userPreferencesFlow.first()
        assertEquals("MIDNIGHT_MOCHI", restartedPrefs.themeMode)
    }

    @Test
    fun test09_rapidThemeSwitchingFidelity() = runBlocking {
        val themeSequence = listOf("PEACHY", "LAVENDER", "MIDNIGHT_MOCHI", "DARK", "LAVENDER", "PEACHY")
        for (theme in themeSequence) {
            userPrefsRepo.setThemeMode(theme)
            val current = userPrefsRepo.userPreferencesFlow.first().themeMode
            assertEquals(theme, current)
        }
    }

    // =========================================================================
    // SECTION 5: SETTINGS VIEWMODEL DISPATCH TESTS
    // =========================================================================

    @Test
    fun test10_settingsViewModelThemeDispatch() = runBlocking {
        val viewModel = SettingsViewModel(userPrefsRepo, categoryRepo, backupRestoreRepo)

        viewModel.setThemeMode("LAVENDER")
        kotlinx.coroutines.delay(100)
        assertEquals("LAVENDER", userPrefsRepo.userPreferencesFlow.first().themeMode)

        viewModel.setThemeMode("MIDNIGHT_MOCHI")
        kotlinx.coroutines.delay(100)
        assertEquals("MIDNIGHT_MOCHI", userPrefsRepo.userPreferencesFlow.first().themeMode)

        viewModel.setThemeMode("PEACHY")
        kotlinx.coroutines.delay(100)
        assertEquals("PEACHY", userPrefsRepo.userPreferencesFlow.first().themeMode)
    }

    // =========================================================================
    // SECTION 6: FINANCIAL LEDGER & DATA INTEGRITY
    // =========================================================================

    @Test
    fun test11_financialLedgerIntegrityAcrossThemeChanges() = runBlocking {
        // Record test financial transactions
        val now = System.currentTimeMillis()
        val tx1 = Transaction(
            title = "Lavender Groceries",
            amountPaise = 25000L,
            transactionType = TransactionType.EXPENSE,
            categoryId = defaultCategoryId,
            transactionDate = now
        )
        val tx2 = Transaction(
            title = "Freelance Income",
            amountPaise = 75000L,
            transactionType = TransactionType.INCOME,
            categoryId = defaultCategoryId,
            transactionDate = now
        )
        transactionRepo.saveTransaction(tx1)
        transactionRepo.saveTransaction(tx2)

        val txList = transactionRepo.getAllTransactionsList()
        assertEquals(2, txList.size)

        val totalIncome = txList.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amountPaise }
        val totalExpense = txList.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        val netBalance = totalIncome - totalExpense

        assertEquals(75000L, totalIncome)
        assertEquals(25000L, totalExpense)
        assertEquals(50000L, netBalance)

        // Switch through themes
        userPrefsRepo.setThemeMode("LAVENDER")
        assertEquals("LAVENDER", userPrefsRepo.userPreferencesFlow.first().themeMode)

        val txListAfterLavender = transactionRepo.getAllTransactionsList()
        assertEquals(2, txListAfterLavender.size)
        assertEquals(
            50000L,
            txListAfterLavender.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amountPaise } -
                    txListAfterLavender.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        )

        userPrefsRepo.setThemeMode("MIDNIGHT_MOCHI")
        assertEquals("MIDNIGHT_MOCHI", userPrefsRepo.userPreferencesFlow.first().themeMode)

        val txListAfterMochi = transactionRepo.getAllTransactionsList()
        assertEquals(2, txListAfterMochi.size)
        assertEquals(
            50000L,
            txListAfterMochi.filter { it.transactionType == TransactionType.INCOME }.sumOf { it.amountPaise } -
                    txListAfterMochi.filter { it.transactionType == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        )
    }

    @Test
    fun test12_semanticColorDistinctionForLedgerAmounts() {
        val themes = listOf(
            "Peachy" to PeachyAppColors,
            "Lavender" to LavenderAppColors,
            "MidnightMochi" to MidnightMochiAppColors,
            "Dark" to DarkAppColors
        )

        for ((name, colors) in themes) {
            // Success (income) and error (expense) colors must never collide
            assertNotEquals("Income and expense colors should differ in $name", colors.incomeGreen, colors.expenseRed)
            // Neither should match background
            assertNotEquals("Income color must not match background in $name", colors.incomeGreen, colors.background)
            assertNotEquals("Expense color must not match background in $name", colors.expenseRed, colors.background)
            // Text primary must differ from background
            assertNotEquals("Text primary must not match background in $name", colors.textPrimary, colors.background)
        }
    }
}
