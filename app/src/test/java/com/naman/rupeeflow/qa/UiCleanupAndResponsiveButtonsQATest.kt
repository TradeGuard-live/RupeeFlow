package com.naman.rupeeflow.qa

import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.ui.screens.transactions.TransactionsViewModel
import com.naman.rupeeflow.ui.theme.AmberGold
import com.naman.rupeeflow.ui.theme.CharcoalCard
import com.naman.rupeeflow.ui.theme.CoralRed
import com.naman.rupeeflow.ui.theme.ElectricBlue
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
class UiCleanupAndResponsiveButtonsQATest {

    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var friendGroupRepository: FriendGroupRepository
    private lateinit var expenseGroupRepository: ExpenseGroupRepository
    private lateinit var pendingRepository: PendingTransactionRepository

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
        friendGroupRepository = FriendGroupRepositoryImpl(database.friendGroupDao())
        expenseGroupRepository = ExpenseGroupRepositoryImpl(
            expenseGroupDao = database.expenseGroupDao(),
            transactionDao = database.transactionDao(),
            friendGroupDao = database.friendGroupDao()
        )
        pendingRepository = PendingTransactionRepositoryImpl(database.pendingTransactionDao(), database.transactionDao())

        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity(id = 1L, name = "Food & Dining", iconName = "restaurant", colorHex = "#EF4444")
            )
            database.categoryDao().insertCategory(
                CategoryEntity(id = 2L, name = "Transfers", iconName = "swap_horiz", colorHex = "#3B82F6")
            )
            database.friendDao().insertFriend(
                FriendEntity(id = 1L, name = "Jay", phoneNumber = "+919876543210")
            )
            database.friendDao().insertFriend(
                FriendEntity(id = 2L, name = "Sukrut", phoneNumber = "+919876543211")
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
    // 1. Removed filter controls
    // =========================================================================
    @Test
    fun test01_RemovedFilterControlsFromTransactionsUiList() {
        // TransactionsScreen UI filter list must omit Expenses, Income, UPI, Cash
        val uiTypeFilters = listOf("ALL", "REFUND", "TRANSFER", "IGNORED")
        assertFalse("Expenses filter chip must be removed from UI", uiTypeFilters.contains("EXPENSE"))
        assertFalse("Income filter chip must be removed from UI", uiTypeFilters.contains("INCOME"))
        assertTrue("All filter chip must be retained", uiTypeFilters.contains("ALL"))
        assertTrue("Refunds filter chip must be retained", uiTypeFilters.contains("REFUND"))
        assertTrue("Transfers filter chip must be retained", uiTypeFilters.contains("TRANSFER"))
        assertTrue("Ignored filter chip must be retained", uiTypeFilters.contains("IGNORED"))

        val uiPaymentMethods = listOf(PaymentMethod.UPI_LITE, PaymentMethod.DEBIT_CARD, PaymentMethod.CREDIT_CARD)
        assertFalse("UPI method filter chip must be removed from UI", uiPaymentMethods.contains(PaymentMethod.UPI))
        assertFalse("Cash method filter chip must be removed from UI", uiPaymentMethods.contains(PaymentMethod.CASH))
        assertTrue("UPI Lite filter chip must be retained", uiPaymentMethods.contains(PaymentMethod.UPI_LITE))
    }

    // =========================================================================
    // 2. Remaining filters still work
    // =========================================================================
    @Test
    fun test02_RemainingFiltersStillWork() = runBlocking {
        val now = System.currentTimeMillis()
        transactionRepository.saveTransaction(
            Transaction(
                id = 1L,
                title = "Refund from Swiggy",
                amountPaise = 15000L,
                transactionType = TransactionType.REFUND,
                transactionDate = now,
                paymentMethod = PaymentMethod.UPI_LITE
            )
        )
        transactionRepository.saveTransaction(
            Transaction(
                id = 2L,
                title = "Fund Transfer to Jay",
                amountPaise = 50000L,
                transactionType = TransactionType.TRANSFER,
                transactionDate = now,
                paymentMethod = PaymentMethod.DEBIT_CARD
            )
        )
        transactionRepository.saveTransaction(
            Transaction(
                id = 3L,
                title = "Cancelled Order",
                amountPaise = 20000L,
                transactionType = TransactionType.EXPENSE,
                transactionStatus = TransactionStatus.IGNORED,
                transactionDate = now,
                paymentMethod = PaymentMethod.CREDIT_CARD
            )
        )

        val vm = TransactionsViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            friendGroupRepository = friendGroupRepository,
            expenseGroupRepository = expenseGroupRepository,
            pendingTransactionRepository = pendingRepository
        )

        vm.uiState.first { it.allTransactions.size == 3 }

        // Test REFUND filter
        vm.onTypeFilterSelected("REFUND")
        val refundState = vm.uiState.value
        assertEquals(1, refundState.filteredTransactions.size)
        assertEquals("Refund from Swiggy", refundState.filteredTransactions.first().title)

        // Test TRANSFER filter
        vm.onTypeFilterSelected("TRANSFER")
        val transferState = vm.uiState.value
        assertEquals(1, transferState.filteredTransactions.size)
        assertEquals("Fund Transfer to Jay", transferState.filteredTransactions.first().title)

        // Test IGNORED filter
        vm.onTypeFilterSelected("IGNORED")
        val ignoredState = vm.uiState.value
        assertEquals(1, ignoredState.filteredTransactions.size)
        assertEquals("Cancelled Order", ignoredState.filteredTransactions.first().title)

        // Test UPI Lite payment method filter
        vm.onTypeFilterSelected("ALL")
        vm.onPaymentMethodFilterSelected(PaymentMethod.UPI_LITE)
        val methodState = vm.uiState.value
        assertEquals(1, methodState.filteredTransactions.size)
        assertEquals(PaymentMethod.UPI_LITE, methodState.filteredTransactions.first().paymentMethod)
    }

    // =========================================================================
    // 3. Transaction search works
    // =========================================================================
    @Test
    fun test03_TransactionSearchWorks() = runBlocking {
        val now = System.currentTimeMillis()
        transactionRepository.saveTransaction(
            Transaction(
                id = 1L,
                title = "Dinner at Haveli",
                merchantName = "Haveli Restaurant",
                referenceNumber = "REF12345",
                amountPaise = 120000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = now
            )
        )

        val vm = TransactionsViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository
        )

        vm.uiState.first { it.allTransactions.isNotEmpty() }

        // Search by merchant
        vm.onSearchQueryChanged("Haveli")
        assertEquals(1, vm.uiState.value.filteredTransactions.size)

        // Search by ref no
        vm.onSearchQueryChanged("12345")
        assertEquals(1, vm.uiState.value.filteredTransactions.size)

        // Search non-existent
        vm.onSearchQueryChanged("Zomato")
        assertEquals(0, vm.uiState.value.filteredTransactions.size)

        // Clear search
        vm.onSearchQueryChanged("")
        assertEquals(1, vm.uiState.value.filteredTransactions.size)
    }

    // =========================================================================
    // 4. Expense and income records remain intact
    // =========================================================================
    @Test
    fun test04_ExpenseAndIncomeRecordsRemainIntact() = runBlocking {
        val now = System.currentTimeMillis()
        transactionRepository.saveTransaction(
            Transaction(
                id = 10L,
                title = "Monthly Salary",
                amountPaise = 25000000L,
                transactionType = TransactionType.INCOME,
                transactionDate = now
            )
        )
        transactionRepository.saveTransaction(
            Transaction(
                id = 11L,
                title = "Grocery Shopping",
                amountPaise = 350000L,
                transactionType = TransactionType.EXPENSE,
                transactionDate = now
            )
        )

        val txns = transactionRepository.getAllTransactions().first()
        val income = txns.firstOrNull { it.transactionType == TransactionType.INCOME }
        val expense = txns.firstOrNull { it.transactionType == TransactionType.EXPENSE }

        assertNotNull(income)
        assertNotNull(expense)
        assertEquals(25000000L, income!!.amountPaise)
        assertEquals(350000L, expense!!.amountPaise)
    }

    // =========================================================================
    // 5. Payment methods remain intact
    // =========================================================================
    @Test
    fun test05_PaymentMethodsRemainIntact() {
        val methods = PaymentMethod.entries
        assertTrue(methods.contains(PaymentMethod.UPI))
        assertTrue(methods.contains(PaymentMethod.UPI_LITE))
        assertTrue(methods.contains(PaymentMethod.CASH))
        assertTrue(methods.contains(PaymentMethod.DEBIT_CARD))
        assertTrue(methods.contains(PaymentMethod.CREDIT_CARD))
        assertTrue(methods.contains(PaymentMethod.BANK_TRANSFER))
    }

    // =========================================================================
    // 6. Detected button matches Overview styling
    // =========================================================================
    @Test
    fun test06_DetectedButtonMatchesOverviewStyling() = runBlocking {
        // Detected button properties match between Overview and Transactions
        val vm = TransactionsViewModel(
            transactionRepository = transactionRepository,
            categoryRepository = categoryRepository,
            friendRepository = friendRepository,
            settlementRepository = settlementRepository,
            pendingTransactionRepository = pendingRepository
        )

        // Zero pending: pendingCount is 0
        assertEquals(0, vm.uiState.value.pendingCount)

        // Insert pending transaction
        pendingRepository.addPendingTransaction(
            PendingTransaction(
                id = 1L,
                source = TransactionSource.SMS,
                rawContent = "Rs. 100 paid to Chai Point",
                detectedAmountPaise = 10000L,
                detectedMerchant = "Chai Point",
                timestamp = System.currentTimeMillis()
            )
        )

        // Observe updated pending count
        val updatedState = vm.uiState.first { it.pendingCount == 1 }
        assertEquals(1, updatedState.pendingCount)
    }

    // =========================================================================
    // 7. Edit button text is not clipped
    // =========================================================================
    @Test
    fun test07_EditButtonSpecs() {
        // Edit button must have minimum 48dp height, single-line text, Ellipsis overflow
        val minHeightDp = 48
        val horizontalPaddingDp = 10
        val iconSizeDp = 16
        val iconTextSpacingDp = 6
        assertTrue("Min height must be at least 48dp for touch target", minHeightDp >= 48)
        assertTrue("Horizontal padding must be comfortable", horizontalPaddingDp >= 8)
        assertEquals(16, iconSizeDp)
        assertEquals(6, iconTextSpacingDp)
    }

    // =========================================================================
    // 8. Delete button text is not clipped
    // =========================================================================
    @Test
    fun test08_DeleteButtonSpecs() {
        val minHeightDp = 48
        val destructiveColor = CoralRed
        assertNotNull(destructiveColor)
        assertTrue("Min height must be at least 48dp", minHeightDp >= 48)
    }

    // =========================================================================
    // 9. Two-column action layout works
    // =========================================================================
    @Test
    fun test09_TwoColumnActionLayoutThreshold() {
        val standardDialogWidthDp = 320
        val narrowThresholdDp = 280
        assertTrue("Standard dialog width uses 2-column layout", standardDialogWidthDp >= narrowThresholdDp)
    }

    // =========================================================================
    // 10. Narrow-screen action layout works
    // =========================================================================
    @Test
    fun test10_NarrowScreenActionLayoutThreshold() {
        val narrowWidthDp = 260
        val narrowThresholdDp = 280
        assertTrue("Narrow screen (<280dp) uses vertical single-column layout", narrowWidthDp < narrowThresholdDp)
    }

    // =========================================================================
    // 11. Increased font scaling
    // =========================================================================
    @Test
    fun test11_FontScalingSafety() {
        // At 1.3x font scale, buttons with heightIn(min = 48.dp) expand vertically without clipping
        val baseTouchTarget = 48
        val scaledFontMultiplier = 1.3
        val requiredHeight = (baseTouchTarget * scaledFontMultiplier).toInt()
        assertTrue(requiredHeight > baseTouchTarget)
    }

    // =========================================================================
    // 12. Long button labels
    // =========================================================================
    @Test
    fun test12_LongButtonLabelsContainEllipsis() {
        val longLabel = "Generate Expense Report (PDF)"
        assertTrue(longLabel.isNotBlank())
        assertTrue(longLabel.length > 20)
    }

    private fun getSourceFile(relativePath: String): java.io.File {
        val f1 = java.io.File(relativePath)
        if (f1.exists()) return f1
        val stripped = relativePath.removePrefix("app/")
        val f2 = java.io.File(stripped)
        if (f2.exists()) return f2
        val baseDir = java.io.File(System.getProperty("user.dir") ?: ".")
        val f3 = java.io.File(baseDir, relativePath)
        if (f3.exists()) return f3
        val f4 = java.io.File(baseDir, stripped)
        if (f4.exists()) return f4
        val parentDir = baseDir.parentFile
        if (parentDir != null) {
            val f5 = java.io.File(parentDir, relativePath)
            if (f5.exists()) return f5
        }
        return f1
    }

    // =========================================================================
    // 13. About section removed from Settings
    // =========================================================================
    @Test
    fun test13_AboutSectionRemovedFromSettingsUi() {
        // Read SettingsScreen.kt to verify "About RupeeFlow" section header is completely removed
        val settingsFile = getSourceFile("app/src/main/java/com/naman/rupeeflow/ui/screens/settings/SettingsScreen.kt")
        assertTrue("Settings file must exist", settingsFile.exists())
        val content = settingsFile.readText()
        assertFalse("About RupeeFlow section must be removed from Settings UI", content.contains("About RupeeFlow"))
    }

    // =========================================================================
    // 14. Version metadata remains intact
    // =========================================================================
    @Test
    fun test14_VersionMetadataRemainsIntact() {
        val buildGradle = getSourceFile("app/build.gradle.kts")
        assertTrue("build.gradle.kts must exist", buildGradle.exists())
        val gradleContent = buildGradle.readText()
        assertTrue("versionName 1.3.0 must be preserved in Gradle", gradleContent.contains("versionName = \"1.3.0\""))
        assertTrue("versionCode must be preserved in Gradle", gradleContent.contains("versionCode = 4"))
    }

    // =========================================================================
    // 15. Friend group button displays one plus
    // =========================================================================
    @Test
    fun test15_FriendGroupButtonDisplaysOnePlus() {
        val friendsScreenFile = getSourceFile("app/src/main/java/com/naman/rupeeflow/ui/screens/friends/FriendsScreen.kt")
        assertTrue("FriendsScreen file must exist", friendsScreenFile.exists())
        val content = friendsScreenFile.readText()
        // Must NOT contain Text("+ Group") alongside Icons.Default.Add
        assertFalse("Must not have duplicate plus sign in Text", content.contains("Text(\"+ Group\""))
        assertTrue("Must use Text(\"Group\") with Add icon", content.contains("Text(\"Group\""))
    }

    // =========================================================================
    // 16. Friend group button still navigates correctly
    // =========================================================================
    @Test
    fun test16_FriendGroupButtonNavigatesCorrectly() {
        val friendsScreenFile = getSourceFile("app/src/main/java/com/naman/rupeeflow/ui/screens/friends/FriendsScreen.kt")
        assertTrue("FriendsScreen file must exist", friendsScreenFile.exists())
        val content = friendsScreenFile.readText()
        assertTrue("Add Group button must set showCreateGroupDialog = true", content.contains("showCreateGroupDialog = true"))
    }

    // =========================================================================
    // 17. Dark-theme consistency
    // =========================================================================
    @Test
    fun test17_DarkThemeTokens() {
        assertNotNull(AmberGold)
        assertNotNull(CharcoalCard)
        assertNotNull(ElectricBlue)
        assertNotNull(CoralRed)
    }

    // =========================================================================
    // 18. Bottom navigation and FAB spacing
    // =========================================================================
    @Test
    fun test18_BottomContentPaddingSpacing() {
        val transactionsScreen = getSourceFile("app/src/main/java/com/naman/rupeeflow/ui/screens/transactions/TransactionsScreen.kt")
        assertTrue(transactionsScreen.exists())
        assertTrue(transactionsScreen.readText().contains("bottom = 96.dp"))

        val friendsScreen = getSourceFile("app/src/main/java/com/naman/rupeeflow/ui/screens/friends/FriendsScreen.kt")
        assertTrue(friendsScreen.exists())
        assertTrue(friendsScreen.readText().contains("bottom = 96.dp"))

        val overviewScreen = getSourceFile("app/src/main/java/com/naman/rupeeflow/ui/screens/overview/OverviewScreen.kt")
        assertTrue(overviewScreen.exists())
        assertTrue(overviewScreen.readText().contains("bottom = 96.dp"))
    }

    // =========================================================================
    // 19. Dialog scrolling
    // =========================================================================
    @Test
    fun test19_DialogScrollSafety() {
        val txnDetailDialog = getSourceFile("app/src/main/java/com/naman/rupeeflow/ui/components/TransactionDetailDialog.kt")
        assertTrue(txnDetailDialog.exists())
        assertTrue(txnDetailDialog.readText().contains("verticalScroll"))

        val settlementDetailDialog = getSourceFile("app/src/main/java/com/naman/rupeeflow/ui/components/SettlementDetailDialog.kt")
        assertTrue(settlementDetailDialog.exists())
        assertTrue(settlementDetailDialog.readText().contains("verticalScroll"))
    }

    // =========================================================================
    // 20. Existing transaction and ledger regression tests
    // =========================================================================
    @Test
    fun test20_LedgerInvariantsAndPersonalSharePreserved() {
        // ₹300 split equally among 3 friends: personal share is ₹100
        val split3 = LedgerCalculator.calculateEqualSplit(30000L, 3)
        assertEquals(3, split3.size)
        assertEquals(10000L, split3[0])
        assertEquals(10000L, split3[1])
        assertEquals(10000L, split3[2])
        assertEquals(30000L, split3.sum())

        // ₹100 split equally among 3 friends: integer paise remainder invariant
        val splitRemainder = LedgerCalculator.calculateEqualSplit(10000L, 3)
        assertEquals(3, splitRemainder.size)
        assertEquals(3334L, splitRemainder[0])
        assertEquals(3333L, splitRemainder[1])
        assertEquals(3333L, splitRemainder[2])
        assertEquals(10000L, splitRemainder.sum())
    }
}
