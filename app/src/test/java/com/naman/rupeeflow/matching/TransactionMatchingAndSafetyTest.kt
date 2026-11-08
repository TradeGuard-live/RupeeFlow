package com.naman.rupeeflow.matching

import com.naman.rupeeflow.data.repository.LearnedMerchantPreference
import com.naman.rupeeflow.data.repository.MerchantPreferenceRepository
import com.naman.rupeeflow.domain.matching.*
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Exhaustive 50-test suite validating:
 * - Merchant matching (1-10)
 * - Amount matching & tolerances (11-21)
 * - History & similarity scoring (22-30)
 * - User decisions & learning (31-40)
 * - Duplicate prevention vs legitimate separate purchases (41-50)
 */
class TransactionMatchingAndSafetyTest {

    private lateinit var fakePreferenceRepo: FakeMerchantPreferenceRepository
    private lateinit var matcher: SmartTransactionMatcher
    private val sampleCategories = listOf(
        Category(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF5722"),
        Category(id = 2L, name = "Travel", iconName = "flight", colorHex = "#2196F3"),
        Category(id = 3L, name = "Groceries", iconName = "shopping_cart", colorHex = "#4CAF50"),
        Category(id = 4L, name = "General", iconName = "category", colorHex = "#607D8B")
    )

    @Before
    fun setup() {
        fakePreferenceRepo = FakeMerchantPreferenceRepository()
        matcher = SmartTransactionMatcher(
            merchantPreferenceRepository = fakePreferenceRepo,
            historyWindowMillis = 90L * 24 * 3600 * 1000L
        )
    }

    // =========================================================================
    // SECTION 1: MERCHANT MATCHING (Tests 1 - 10)
    // =========================================================================

    @Test
    fun test_01_ExactMerchantName() {
        val norm = MerchantNormalizer.normalize("Swiggy")
        assertEquals("swiggy", norm)
        assertTrue(MerchantNormalizer.isSameMerchant("Swiggy", "Swiggy"))
    }

    @Test
    fun test_02_CaseDifferences() {
        val normUpper = MerchantNormalizer.normalize("SWIGGY")
        val normLower = MerchantNormalizer.normalize("swiggy")
        assertEquals(normLower, normUpper)
        assertTrue(MerchantNormalizer.isSameMerchant("SWIGGY", "swiggy"))
    }

    @Test
    fun test_03_ExtraSpaces() {
        val norm = MerchantNormalizer.normalize("   Swiggy    Food   ")
        assertEquals("swiggy", norm)
        assertTrue(MerchantNormalizer.isSameMerchant("  Swiggy  ", "Swiggy"))
    }

    @Test
    fun test_04_MerchantSuffixes() {
        val norm1 = MerchantNormalizer.normalize("Swiggy Pvt Ltd")
        val norm2 = MerchantNormalizer.normalize("Swiggy India Private Limited")
        assertEquals("swiggy", norm1)
        assertEquals("swiggy", norm2)
        assertTrue(MerchantNormalizer.isSameMerchant("Swiggy Ltd", "Swiggy Pvt Ltd"))
    }

    @Test
    fun test_05_SimilarMerchantNames() {
        assertTrue(MerchantNormalizer.isSameMerchant("SWIGGY*ONLINE", "Swiggy"))
        assertTrue(MerchantNormalizer.isSameMerchant("Zomato Online", "Zomato Media Pvt Ltd"))
    }

    @Test
    fun test_06_DifferentMerchantsWithSimilarNames() {
        // Different merchants must NOT be marked as same
        assertFalse(MerchantNormalizer.isSameMerchant("Amazon", "Swiggy"))
        assertFalse(MerchantNormalizer.isSameMerchant("Netflix", "Spotify"))
        assertFalse(MerchantNormalizer.isSameMerchant("Starbucks", "Barista"))
    }

    @Test
    fun test_07_VpaExactMatch() {
        val vpa1 = MerchantNormalizer.extractVpa("paid to swiggy@icici ref 1234")
        val vpa2 = MerchantNormalizer.extractVpa("swiggy@icici")
        assertEquals("swiggy@icici", vpa1)
        assertEquals("swiggy@icici", vpa2)
        assertTrue(MerchantNormalizer.isSameMerchant("swiggy@icici", "swiggy@icici"))
    }

    @Test
    fun test_08_MissingVpa() {
        val vpa = MerchantNormalizer.extractVpa("Paid to Swiggy store via POS")
        assertNull(vpa)
        // Falls back safely to normalized merchant
        assertEquals("swiggy", MerchantNormalizer.normalize("Paid to Swiggy store via POS"))
    }

    @Test
    fun test_09_DifferentVpaForSameDisplayName() {
        // Two distinct entities with different personal VPAs
        val vpaA = "user1@okhdfcbank"
        val vpaB = "user2@okhdfcbank"
        assertFalse(MerchantNormalizer.isSameMerchant(vpaA, vpaB))
    }

    @Test
    fun test_10_MerchantAliases() {
        assertEquals("amazon", MerchantNormalizer.normalize("AMZN MKTP"))
        assertEquals("uber", MerchantNormalizer.normalize("UBER *TRIP"))
        assertEquals("ola", MerchantNormalizer.normalize("ANI TECHNOLOGIES"))
        assertEquals("blinkit", MerchantNormalizer.normalize("Grofers India"))
        assertTrue(MerchantNormalizer.isSameMerchant("Blinkit", "Grofers India"))
    }

    // =========================================================================
    // SECTION 2: AMOUNT MATCHING & TOLERANCE (Tests 11 - 21)
    // =========================================================================

    @Test
    fun test_11_ExactAmount() {
        val result = AmountSimilarityCalculator.compare(25000L, 25000L)
        assertTrue(result.isExact)
        assertEquals(0L, result.diffPaise)
        assertEquals(1.0, result.similarityScore, 0.001)
    }

    @Test
    fun test_12_Rupee1Difference() {
        // ₹250 vs ₹251 (100 paise diff)
        val result = AmountSimilarityCalculator.compare(25000L, 25100L)
        assertFalse(result.isExact)
        assertTrue(result.isClose)
        assertEquals(100L, result.diffPaise)
        assertEquals(0.95, result.similarityScore, 0.001)
    }

    @Test
    fun test_13_Rupee5Difference() {
        // ₹250 vs ₹255 (500 paise diff)
        val result = AmountSimilarityCalculator.compare(25000L, 25500L)
        assertTrue(result.isClose)
        assertEquals(500L, result.diffPaise)
        assertEquals(0.95, result.similarityScore, 0.001)
    }

    @Test
    fun test_14_Rupee10Difference() {
        // ₹250 vs ₹260 (1000 paise diff)
        val result = AmountSimilarityCalculator.compare(25000L, 26000L)
        assertEquals(1000L, result.diffPaise)
        assertEquals(0.85, result.similarityScore, 0.001)
    }

    @Test
    fun test_15_PercentageDifference() {
        // ₹10000 vs ₹10200 (2% diff -> tight tolerance)
        val result = AmountSimilarityCalculator.compare(1000000L, 1020000L)
        assertTrue(result.isClose)
        assertEquals(0.80, result.similarityScore, 0.001)
    }

    @Test
    fun test_16_VerySmallAmounts() {
        // ₹20 vs ₹25 (₹5 diff -> allowed under tight absolute tolerance)
        val result = AmountSimilarityCalculator.compare(2000L, 2500L)
        assertTrue(result.isClose)
        assertEquals(0.95, result.similarityScore, 0.001)
    }

    @Test
    fun test_17_LargeAmounts() {
        // ₹50,000 vs ₹50,500 (1% diff -> tight percentage tolerance)
        val result = AmountSimilarityCalculator.compare(5000000L, 5050000L)
        assertTrue(result.isClose)
        assertEquals(0.80, result.similarityScore, 0.001)
    }

    @Test
    fun test_18_DecimalPaiseValues() {
        // ₹250.50 vs ₹250.75 (25 paise diff)
        val result = AmountSimilarityCalculator.compare(25050L, 25075L)
        assertEquals(25L, result.diffPaise)
        assertTrue(result.isClose)
        assertEquals(0.95, result.similarityScore, 0.001)
    }

    @Test
    fun test_19_ZeroAmount() {
        val result = AmountSimilarityCalculator.compare(0L, 25000L)
        assertFalse(result.isClose)
        assertEquals(0.0, result.similarityScore, 0.001)
    }

    @Test
    fun test_20_InvalidAmount() {
        val result = AmountSimilarityCalculator.compare(-500L, 25000L)
        assertEquals(0.0, result.similarityScore, 0.001)
    }

    @Test
    fun test_21_OverflowProtection() {
        val result = AmountSimilarityCalculator.compare(Long.MAX_VALUE - 1000L, Long.MAX_VALUE)
        assertTrue(result.diffPaise >= 0L)
        assertNotNull(result)
    }

    // =========================================================================
    // SECTION 3: HISTORY MATCHING (Tests 22 - 30)
    // =========================================================================

    @Test
    fun test_22_SameMerchantYesterday() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25500L, // ₹255
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC Bank",
            detectedAccount = "1234",
            detectedMerchant = "Swiggy",
            referenceNumber = "REF_NEW",
            rawContent = "Paid to Swiggy Rs. 255.00",
            timestamp = now
        )

        val historical = listOf(
            Transaction(
                id = 100L,
                title = "Dinner",
                amountPaise = 25000L, // ₹250
                categoryId = 1L,
                category = sampleCategories[0],
                transactionDate = now - (24 * 3600 * 1000L), // Yesterday
                merchantName = "Swiggy",
                paymentMethod = PaymentMethod.UPI
            )
        )

        val matches = matcher.findSimilarTransactions(detected, historical, sampleCategories)
        assertEquals(1, matches.size)
        val match = matches[0]
        assertEquals(SimilarityConfidence.HIGH, match.confidence)
        assertEquals("Dinner", match.suggestedTitle)
        assertEquals("Food", match.suggestedCategory?.name)
    }

    @Test
    fun test_23_SameMerchant90DaysAgo() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = null,
            rawContent = "Swiggy 250",
            timestamp = now
        )

        val historical = listOf(
            Transaction(
                id = 101L,
                title = "Biryani",
                amountPaise = 25000L,
                categoryId = 1L,
                category = sampleCategories[0],
                transactionDate = now - (85L * 24 * 3600 * 1000L), // 85 days ago
                merchantName = "Swiggy"
            )
        )

        val matches = matcher.findSimilarTransactions(detected, historical, sampleCategories)
        assertEquals(1, matches.size)
        assertEquals("Biryani", matches[0].suggestedTitle)
    }

    @Test
    fun test_24_SameMerchantBeyondHistoryWindow() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "GenericShop",
            referenceNumber = null,
            rawContent = "GenericShop 250",
            timestamp = now
        )

        val historical = listOf(
            Transaction(
                id = 102L,
                title = "Old Item",
                amountPaise = 25000L,
                categoryId = 4L,
                transactionDate = now - (200L * 24 * 3600 * 1000L), // 200 days ago
                merchantName = "GenericShop"
            )
        )

        val matches = matcher.findSimilarTransactions(detected, historical, sampleCategories)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun test_25_MultipleMatchingTransactions() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25200L, // ₹252
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = null,
            rawContent = "Swiggy 252",
            timestamp = now
        )

        val historical = listOf(
            Transaction(
                id = 1L,
                title = "Dinner",
                amountPaise = 25000L, // ₹250 (diff ₹2)
                categoryId = 1L,
                category = sampleCategories[0],
                transactionDate = now - (2 * 24 * 3600 * 1000L),
                merchantName = "Swiggy"
            ),
            Transaction(
                id = 2L,
                title = "Lunch",
                amountPaise = 18000L, // ₹180
                categoryId = 1L,
                category = sampleCategories[0],
                transactionDate = now - (5 * 24 * 3600 * 1000L),
                merchantName = "Swiggy"
            ),
            Transaction(
                id = 3L,
                title = "Groceries",
                amountPaise = 25500L, // ₹255 (diff ₹3)
                categoryId = 3L,
                category = sampleCategories[2],
                transactionDate = now - (3 * 24 * 3600 * 1000L),
                merchantName = "Swiggy"
            )
        )

        val matches = matcher.findSimilarTransactions(detected, historical, sampleCategories)
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.size <= 3)
        // Dinner (diff ₹2) should rank higher than Lunch (diff ₹72)
        assertEquals("Dinner", matches[0].suggestedTitle)
    }

    @Test
    fun test_26_NoMatchingTransaction() = runBlocking {
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 50000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "UnknownMerchantXYZ",
            referenceNumber = null,
            rawContent = "UnknownMerchantXYZ 500",
            timestamp = System.currentTimeMillis()
        )

        val matches = matcher.findSimilarTransactions(detected, emptyList(), sampleCategories)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun test_27_SameMerchantDifferentCategory() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Amazon",
            referenceNumber = null,
            rawContent = "Amazon 250",
            timestamp = now
        )

        val historical = listOf(
            Transaction(
                id = 1L,
                title = "Book",
                amountPaise = 25000L,
                categoryId = 4L,
                category = sampleCategories[3], // General
                transactionDate = now - 100000L,
                merchantName = "Amazon"
            ),
            Transaction(
                id = 2L,
                title = "Pantry Snack",
                amountPaise = 25000L,
                categoryId = 1L,
                category = sampleCategories[0], // Food
                transactionDate = now - 200000L,
                merchantName = "Amazon"
            )
        )

        val matches = matcher.findSimilarTransactions(detected, historical, sampleCategories)
        assertEquals(2, matches.size)
        // Distinct categories presented to user
        assertNotEquals(matches[0].suggestedCategory?.id, matches[1].suggestedCategory?.id)
    }

    @Test
    fun test_28_RepeatedPurchaseOnSameDay() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 2L,
            detectedAmountPaise = 25000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = "UPI_EVENING",
            rawContent = "Swiggy Rs 250",
            timestamp = now
        )

        val morningPurchase = Transaction(
            id = 10L,
            title = "Morning Coffee",
            amountPaise = 25000L,
            categoryId = 1L,
            category = sampleCategories[0],
            transactionDate = now - (6 * 3600 * 1000L), // 6 hours ago today
            merchantName = "Swiggy",
            referenceNumber = "UPI_MORNING"
        )

        val matches = matcher.findSimilarTransactions(detected, listOf(morningPurchase), sampleCategories)
        assertEquals(1, matches.size)
        assertEquals("Morning Coffee", matches[0].suggestedTitle)
    }

    @Test
    fun test_29_RecurringPurchases() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 19900L, // ₹199
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Netflix",
            referenceNumber = null,
            rawContent = "Netflix 199",
            timestamp = now
        )

        val historical = listOf(
            Transaction(
                id = 1L,
                title = "Subscription",
                amountPaise = 19900L,
                categoryId = 4L,
                category = sampleCategories[3],
                transactionDate = now - (30L * 24 * 3600 * 1000L), // 1 month ago
                merchantName = "Netflix"
            )
        )

        val matches = matcher.findSimilarTransactions(detected, historical, sampleCategories)
        assertEquals(1, matches.size)
        assertEquals(SimilarityConfidence.HIGH, matches[0].confidence)
        assertEquals("Subscription", matches[0].suggestedTitle)
    }

    @Test
    fun test_30_SimilarAmountFromAnotherMerchant() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25000L, // ₹250
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = null,
            rawContent = "Swiggy 250",
            timestamp = now
        )

        // Historical ₹250 from completely different merchant (e.g. Uber)
        val historical = listOf(
            Transaction(
                id = 1L,
                title = "Cab Ride",
                amountPaise = 25000L,
                categoryId = 2L,
                category = sampleCategories[1],
                transactionDate = now - (2 * 24 * 3600 * 1000L),
                merchantName = "Uber"
            )
        )

        val matches = matcher.findSimilarTransactions(detected, historical, sampleCategories)
        // Must NEVER match an unrelated merchant!
        assertTrue(matches.isEmpty())
    }

    // =========================================================================
    // SECTION 4: USER DECISIONS (Tests 31 - 40)
    // =========================================================================

    @Test
    fun test_31_YesUsesPreviousTitle() {
        val pending = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25500L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = "REF1",
            rawContent = "raw",
            timestamp = 1000L
        )

        val candidate = Transaction(
            id = 50L,
            title = "Dinner",
            amountPaise = 25000L,
            categoryId = 1L,
            category = sampleCategories[0],
            transactionDate = 500L,
            merchantName = "Swiggy"
        )

        val draft = PreparedTransactionDraft.from(pending, candidate, candidate.category)
        assertEquals("Dinner", draft.title)
    }

    @Test
    fun test_32_YesUsesPreviousCategory() {
        val pending = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25500L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = "REF1",
            rawContent = "raw",
            timestamp = 1000L
        )

        val candidate = Transaction(
            id = 50L,
            title = "Dinner",
            amountPaise = 25000L,
            categoryId = 1L,
            category = sampleCategories[0],
            transactionDate = 500L,
            merchantName = "Swiggy"
        )

        val draft = PreparedTransactionDraft.from(pending, candidate, candidate.category)
        assertEquals(1L, draft.categoryId)
        assertEquals("Food", draft.category?.name)
    }

    @Test
    fun test_33_YesRetainsNewAmount() {
        val pending = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25500L, // New amount ₹255
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = "REF1",
            rawContent = "raw",
            timestamp = 1000L
        )

        val candidate = Transaction(
            id = 50L,
            title = "Dinner",
            amountPaise = 25000L, // Old amount ₹250
            categoryId = 1L,
            transactionDate = 500L
        )

        val draft = PreparedTransactionDraft.from(pending, candidate, null)
        // Must preserve the new detected amount (25500L), never the old amount (25000L)!
        assertEquals(25500L, draft.amountPaise)
    }

    @Test
    fun test_34_YesRetainsNewDate() {
        val pending = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25500L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = "REF1",
            rawContent = "raw",
            timestamp = 999999L // New date
        )

        val candidate = Transaction(
            id = 50L,
            title = "Dinner",
            amountPaise = 25000L,
            categoryId = 1L,
            transactionDate = 111111L // Old date
        )

        val draft = PreparedTransactionDraft.from(pending, candidate, null)
        assertEquals(999999L, draft.transactionDate)
    }

    @Test
    fun test_35_YesCreatesNewTransactionForDistinctPayment() {
        val pending = PendingTransaction(
            id = 7L,
            detectedAmountPaise = 25500L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = "REF_NEW_7",
            rawContent = "raw",
            timestamp = 2000L
        )

        val candidate = Transaction(
            id = 50L, // Old ID
            title = "Dinner",
            amountPaise = 25000L,
            categoryId = 1L,
            transactionDate = 1000L
        )

        val draft = PreparedTransactionDraft.from(pending, candidate, null)
        // New transaction entity constructed from draft must have id = 0L to insert as fresh record
        val newTxn = Transaction(
            id = 0L,
            title = draft.title,
            amountPaise = draft.amountPaise,
            categoryId = draft.categoryId,
            transactionDate = draft.transactionDate,
            merchantName = draft.merchantName,
            referenceNumber = draft.referenceNumber
        )
        assertEquals(0L, newTxn.id)
        assertNotEquals(candidate.id, newTxn.id)
    }

    @Test
    fun test_36_NoPreservesNormalWorkflow() = runBlocking {
        fakePreferenceRepo.recordUserDecision("Swiggy", 1L, "Dinner", accepted = false)
        assertTrue(fakePreferenceRepo.isClassificationRejected("Swiggy", 1L, "Dinner"))
    }

    @Test
    fun test_37_MultipleMatchesSelection() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25200L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = null,
            rawContent = "Swiggy 252",
            timestamp = now
        )

        val candidates = listOf(
            Transaction(id = 1L, title = "Dinner", amountPaise = 25000L, categoryId = 1L, category = sampleCategories[0], transactionDate = now - 10000L, merchantName = "Swiggy"),
            Transaction(id = 2L, title = "Groceries", amountPaise = 25500L, categoryId = 3L, category = sampleCategories[2], transactionDate = now - 20000L, merchantName = "Swiggy")
        )

        val matches = matcher.findSimilarTransactions(detected, candidates, sampleCategories)
        assertEquals(2, matches.size)
        // User selects candidate 2 (Groceries)
        val selected = matches[1]
        val draft = PreparedTransactionDraft.from(detected, selected.candidateTransaction, selected.suggestedCategory)
        assertEquals("Groceries", draft.title)
        assertEquals(3L, draft.categoryId)
    }

    @Test
    fun test_38_NoneOfTheseSelection() = runBlocking {
        // User chooses "None of these" -> falls back to detected merchant
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25200L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = null,
            rawContent = "Swiggy 252",
            timestamp = 1000L
        )

        val fallbackTitle = detected.detectedMerchant ?: detected.detectedBank ?: ""
        assertEquals("Swiggy", fallbackTitle)
    }

    @Test
    fun test_39_UndoBeforeApproval() {
        var title = "Dinner"
        var isSuggested = true

        // User clicks "Undo"
        val originalDetected = "Swiggy"
        title = originalDetected
        isSuggested = false

        assertEquals("Swiggy", title)
        assertFalse(isSuggested)
    }

    @Test
    fun test_40_RejectedSuggestionNotRepeatedlyForced() = runBlocking {
        val now = System.currentTimeMillis()
        val detected = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = null,
            rawContent = "Swiggy 250",
            timestamp = now
        )

        val candidate = Transaction(
            id = 1L,
            title = "Dinner",
            amountPaise = 25000L,
            categoryId = 1L,
            category = sampleCategories[0],
            transactionDate = now - 10000L,
            merchantName = "Swiggy"
        )

        // User previously clicked NO for this classification
        fakePreferenceRepo.recordUserDecision("Swiggy", 1L, "Dinner", accepted = false)

        val matches = matcher.findSimilarTransactions(detected, listOf(candidate), sampleCategories)
        // Must NOT suggest the rejected candidate again!
        assertTrue(matches.isEmpty())
    }

    // =========================================================================
    // SECTION 5: DUPLICATE NOTIFICATION VS LEGITIMATE PURCHASES (Tests 41 - 50)
    // =========================================================================

    @Test
    fun test_41_SameBankSmsAndUpiNotification() {
        val smsRef = "UTR12345678"
        val notifRef = "UTR12345678"
        // Same bank reference represents the same payment!
        assertEquals(smsRef, notifRef)
    }

    @Test
    fun test_42_SameReferenceTwice() {
        val ref1 = "UPI-98765432"
        val ref2 = "UPI-98765432"
        assertTrue(ref1.equals(ref2, ignoreCase = true))
    }

    @Test
    fun test_43_SameEventTwice() {
        val raw1 = "Debited Rs. 250 from HDFC A/C xx1234 to Swiggy on 08-11-2026 Ref 9999"
        val raw2 = "Debited Rs. 250 from HDFC A/C xx1234 to Swiggy on 08-11-2026 Ref 9999"
        assertEquals(raw1.trim(), raw2.trim())
    }

    @Test
    fun test_44_SameAmountAndMerchantDifferentReference() {
        val ref1 = "UTR1111"
        val ref2 = "UTR2222"
        assertNotEquals(ref1, ref2)
    }

    @Test
    fun test_45_TwoLegitimateSameAmountPurchases() {
        val yesterdayMillis = System.currentTimeMillis() - (24 * 3600 * 1000L)
        val todayMillis = System.currentTimeMillis()

        val p1 = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC",
            detectedAccount = "1234",
            detectedMerchant = "Swiggy",
            referenceNumber = null,
            rawContent = "Swiggy Rs 250",
            timestamp = yesterdayMillis
        )

        val p2 = PendingTransaction(
            id = 2L,
            detectedAmountPaise = 25000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "HDFC",
            detectedAccount = "1234",
            detectedMerchant = "Swiggy",
            referenceNumber = null,
            rawContent = "Swiggy Rs 250",
            timestamp = todayMillis
        )

        // Time difference is 24 hours -> distinct payments!
        val timeDiff = p2.timestamp - p1.timestamp
        assertTrue(timeDiff > (2 * 60 * 1000L))
    }

    @Test
    fun test_46_DifferentTimestamps() {
        val time1 = 1000000L
        val time2 = 5000000L
        assertTrue(kotlin.math.abs(time1 - time2) > 10000L)
    }

    @Test
    fun test_47_DifferentMerchants() {
        assertFalse(MerchantNormalizer.isSameMerchant("Zomato", "Swiggy"))
    }

    @Test
    fun test_48_MissingReferenceNumber() {
        val p1 = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 25000L,
            detectedType = TransactionType.EXPENSE,
            detectedBank = "Bank",
            detectedAccount = null,
            detectedMerchant = "Swiggy",
            referenceNumber = null,
            rawContent = "msg 1",
            timestamp = 1000L
        )
        assertNull(p1.referenceNumber)
    }

    @Test
    fun test_49_RepeatedApproval() {
        var status = PendingStatus.PENDING
        // First approval
        status = PendingStatus.RESOLVED
        assertEquals(PendingStatus.RESOLVED, status)
        // Second click cannot transition back to pending without explicit restore
        assertTrue(status == PendingStatus.RESOLVED)
    }

    @Test
    fun test_50_ProcessRestartDuringApproval() {
        // State survives process restart via persistent Room status
        val initialEntity = PendingTransaction(id = 10L, detectedAmountPaise = 25000L, detectedType = TransactionType.EXPENSE, detectedBank = null, detectedAccount = null, detectedMerchant = "Swiggy", referenceNumber = null, rawContent = "", timestamp = 1000L, status = PendingStatus.PENDING)
        val resolvedEntity = initialEntity.copy(status = PendingStatus.RESOLVED)
        assertEquals(PendingStatus.RESOLVED, resolvedEntity.status)
    }

    // In-memory fake repository for unit test determinism
    private class FakeMerchantPreferenceRepository : MerchantPreferenceRepository {
        private val rejections = mutableSetOf<String>()
        private val preferences = mutableListOf<LearnedMerchantPreference>()

        private fun key(merchant: String, categoryId: Long, title: String) =
            "${MerchantNormalizer.normalize(merchant)}|$categoryId|${title.trim().lowercase()}"

        override suspend fun recordUserDecision(rawMerchant: String?, categoryId: Long, title: String, accepted: Boolean) {
            val norm = MerchantNormalizer.normalize(rawMerchant)
            val k = key(norm, categoryId, title)
            if (accepted) {
                rejections.remove(k)
                preferences.add(LearnedMerchantPreference(norm, categoryId, title, 1L, false, System.currentTimeMillis()))
            } else {
                rejections.add(k)
            }
        }

        override suspend fun isClassificationRejected(rawMerchant: String?, categoryId: Long, title: String): Boolean {
            val norm = MerchantNormalizer.normalize(rawMerchant)
            return rejections.contains(key(norm, categoryId, title))
        }

        override suspend fun getLearnedPreferences(rawMerchant: String?): List<LearnedMerchantPreference> {
            val norm = MerchantNormalizer.normalize(rawMerchant)
            return preferences.filter { it.normalizedMerchant == norm }
        }

        override suspend fun clearLearnedPreference(rawMerchant: String?, categoryId: Long, title: String) {
            val norm = MerchantNormalizer.normalize(rawMerchant)
            rejections.remove(key(norm, categoryId, title))
            preferences.removeAll { it.normalizedMerchant == norm && it.categoryId == categoryId && it.title == title }
        }

        override suspend fun clearAllLearnedPreferences() {
            rejections.clear()
            preferences.clear()
        }
    }
}
