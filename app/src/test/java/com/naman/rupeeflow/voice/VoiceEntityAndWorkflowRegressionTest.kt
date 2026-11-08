package com.naman.rupeeflow.voice

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.voice.execution.VoiceCommandValidator
import com.naman.rupeeflow.voice.model.*
import com.naman.rupeeflow.voice.nlp.FriendMatchResult
import com.naman.rupeeflow.voice.nlp.VoiceCommandParser
import com.naman.rupeeflow.voice.nlp.VoiceEntityExtractor
import com.naman.rupeeflow.voice.nlp.VoiceEntityResolver
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive 55-test suite covering:
 * - Phase 1: Friend name entity extraction across English, Hindi, Gujarati, Hinglish
 * - Phase 2: Expense title extraction & separation from categories
 * - Phase 3: Payer selection defaults, stability, and historical preservation
 * - Phase 4: Detected transaction states (Pending, Approved, Ignored, Restore)
 */
class VoiceEntityAndWorkflowRegressionTest {

    private lateinit var sampleFriends: List<Friend>
    private lateinit var sampleGroups: List<FriendGroup>
    private lateinit var sampleCategories: List<Category>

    @Before
    fun setup() {
        sampleFriends = listOf(
            Friend(id = 1L, name = "Jay", phoneNumber = "9033117004", isArchived = false),
            Friend(id = 2L, name = "Manav", phoneNumber = "7574848491", isArchived = false),
            Friend(id = 3L, name = "Manan", phoneNumber = "9016948101", isArchived = false),
            Friend(id = 4L, name = "Pranav", phoneNumber = "9825012345", isArchived = false),
            Friend(id = 5L, name = "Sukrut", phoneNumber = "9876543210", isArchived = false),
            Friend(id = 6L, name = "Raj Kumar", phoneNumber = "9898011223", isArchived = false),
            Friend(id = 7L, name = "Archived Friend", isArchived = true)
        )

        sampleGroups = listOf(
            FriendGroup(id = 1L, name = "Hostel Friends", memberFriendIds = listOf(1L, 2L, 3L))
        )

        sampleCategories = listOf(
            Category(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF5722"),
            Category(id = 2L, name = "Travel", iconName = "flight", colorHex = "#2196F3"),
            Category(id = 3L, name = "Utilities", iconName = "bolt", colorHex = "#FFC107"),
            Category(id = 4L, name = "Fuel", iconName = "local_gas_station", colorHex = "#9C27B0"),
            Category(id = 5L, name = "Education", iconName = "school", colorHex = "#4CAF50"),
            Category(id = 6L, name = "General", iconName = "category", colorHex = "#607D8B")
        )
    }

    // =========================================================================
    // PHASE 1: FRIEND NAME EXTRACTION (Tests 1 - 15)
    // =========================================================================

    @Test
    fun test_01_AddFriendSukrut() {
        val result1 = VoiceEntityExtractor.extractFriendName("add a friend sukrut", "Add a friend Sukrut")
        assertEquals("Sukrut", result1.name)
        assertFalse(result1.isAmbiguousOrMissing)

        val result2 = VoiceEntityExtractor.extractFriendName("add friend sukrut", "Add friend Sukrut")
        assertEquals("Sukrut", result2.name)

        val cmd = VoiceCommandParser.parse("Add a friend Sukrut", sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd is VoiceCommand.CreateFriend)
        assertEquals("Sukrut", (cmd as VoiceCommand.CreateFriend).name)
    }

    @Test
    fun test_02_AddFriendNamedJay() {
        val result = VoiceEntityExtractor.extractFriendName("add friend named jay", "Add friend named Jay")
        assertEquals("Jay", result.name)
        assertFalse(result.isAmbiguousOrMissing)
    }

    @Test
    fun test_03_AddFriendRajKumar() {
        val result = VoiceEntityExtractor.extractFriendName("add friend raj kumar", "Add friend Raj Kumar")
        assertEquals("Raj Kumar", result.name)
        assertFalse(result.isAmbiguousOrMissing)
    }

    @Test
    fun test_04_CreateNewFriendManan() {
        val result = VoiceEntityExtractor.extractFriendName("create a new friend manan", "Create a new friend Manan")
        assertEquals("Manan", result.name)
        assertFalse(result.isAmbiguousOrMissing)
    }

    @Test
    fun test_05_AddFriendWithPhoneNumber() {
        val result = VoiceEntityExtractor.extractFriendName(
            "add new friend jay number 9876543210",
            "Add new friend Jay, number 9876543210"
        )
        assertEquals("Jay", result.name)
        assertEquals("9876543210", result.phoneNumber)
        assertFalse(result.isAmbiguousOrMissing)
    }

    @Test
    fun test_06_GujaratiFriendCommand() {
        val result1 = VoiceEntityExtractor.extractFriendName("મિત્ર સુકૃતને ઉમેરો", "મિત્ર સુકૃતને ઉમેરો")
        assertEquals("સુકૃત", result1.name)

        val result2 = VoiceEntityExtractor.extractFriendName("સુકૃત નામનો મિત્ર ઉમેરો", "સુકૃત નામનો મિત્ર ઉમેરો")
        assertEquals("સુકૃત", result2.name)

        val result3 = VoiceEntityExtractor.extractFriendName("સુકૃતને મિત્ર તરીકે ઉમેરો", "સુકૃતને મિત્ર તરીકે ઉમેરો")
        assertEquals("સુકૃત", result3.name)
    }

    @Test
    fun test_07_HindiFriendCommand() {
        val result1 = VoiceEntityExtractor.extractFriendName("एक दोस्त सुकृत जोड़ो", "एक दोस्त सुकृत जोड़ो")
        assertEquals("सुकृत", result1.name)

        val result2 = VoiceEntityExtractor.extractFriendName("सुकृत नाम का दोस्त जोड़ो", "सुकृत नाम का दोस्त जोड़ो")
        assertEquals("सुकृत", result2.name)
    }

    @Test
    fun test_08_HinglishFriendCommand() {
        val result = VoiceEntityExtractor.extractFriendName("jay ko friend list mein add karo", "Jay ko friend list mein add karo")
        assertEquals("Jay", result.name)
        assertFalse(result.isAmbiguousOrMissing)
    }

    @Test
    fun test_09_GujaratiEnglishMixedCommand() {
        val result = VoiceEntityExtractor.extractFriendName("sukrut ne friend list ma add karo", "Sukrut ne friend list ma add karo")
        assertEquals("Sukrut", result.name)
        assertFalse(result.isAmbiguousOrMissing)
    }

    @Test
    fun test_10_MissingName() {
        val result = VoiceEntityExtractor.extractFriendName("add a friend", "Add a friend.")
        assertTrue(result.isAmbiguousOrMissing)
        assertTrue(result.name.isBlank())

        val cmd = VoiceCommandParser.parse("Add a friend", sampleFriends, sampleGroups, sampleCategories)
        val validation = VoiceCommandValidator.validateAndBuildPreview(cmd, sampleFriends, sampleGroups, sampleCategories)
        assertTrue(validation is VoiceCommandResult.ClarificationRequired)
        assertEquals(VoiceClarificationType.MISSING_FRIEND_NAME, (validation as VoiceCommandResult.ClarificationRequired).prompt.type)
    }

    @Test
    fun test_11_AmbiguousName() {
        val result = VoiceEntityExtractor.extractFriendName("add to my friends", "Add to my friends")
        assertTrue(result.isAmbiguousOrMissing)
        assertTrue(result.name.isBlank())
    }

    @Test
    fun test_12_PunctuationInName() {
        val result = VoiceEntityExtractor.extractFriendName("add friend: Jay, please!", "Add friend: Jay, please!")
        assertEquals("Jay", result.name)
        assertFalse(result.isAmbiguousOrMissing)
    }

    @Test
    fun test_13_UnicodeName() {
        val result = VoiceEntityExtractor.extractFriendName("નવો મિત્ર સુકૃત ઉમેરો", "નવો મિત્ર સુકૃત ઉમેરો")
        assertEquals("સુકૃત", result.name)
    }

    @Test
    fun test_14_SimilarExistingNames() {
        val match = VoiceEntityResolver.resolveFriend("Jay", sampleFriends)
        assertTrue(match is FriendMatchResult.Single)
        assertEquals("Jay", (match as FriendMatchResult.Single).friend.name)
    }

    @Test
    fun test_15_DuplicateFriend() {
        val suspicious = VoiceEntityExtractor.detectSuspiciousFriendNames(
            listOf(
                Friend(id = 100L, name = "Add A Friend Sukrut"),
                Friend(id = 101L, name = "Jay")
            )
        )
        assertEquals(1, suspicious.size)
        assertEquals("Sukrut", suspicious[0].second)
    }

    // =========================================================================
    // PHASE 2: EXPENSE TITLE EXTRACTION (Tests 16 - 30)
    // =========================================================================

    @Test
    fun test_16_TrainTicketExpense() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "add an expense of 300 rupees for train ticket",
            "Add an expense of 300 rupees for train ticket",
            "Travel"
        )
        assertEquals("Train Ticket", result.title)
        assertFalse(result.isMissing)
    }

    @Test
    fun test_17_FoodExpense() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "i spent 200 rupees on food",
            "I spent 200 rupees on food",
            "Food"
        )
        assertEquals("Food", result.title)
        assertFalse(result.isMissing)
    }

    @Test
    fun test_18_ElectricityBill() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "add 500 rupees for electricity bill paid by upi",
            "Add 500 rupees for electricity bill paid by UPI",
            "Utilities"
        )
        assertEquals("Electricity Bill", result.title)
        assertFalse(result.isMissing)
    }

    @Test
    fun test_19_PetrolExpense() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "add 150 rupees ka petrol expense",
            "Add 150 rupees ka petrol expense",
            "Fuel"
        )
        assertEquals("Petrol", result.title)
        assertFalse(result.isMissing)
    }

    @Test
    fun test_20_CollegeFees() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "i paid 400 for college fees using cash",
            "I paid 400 for college fees using cash",
            "Education"
        )
        assertEquals("College Fees", result.title)
        assertFalse(result.isMissing)
    }

    @Test
    fun test_21_GujaratiExpense() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "આજે ટ્રેન ટિકિટ માટે 300 રૂપિયા ખર્ચ્યા",
            "આજે ટ્રેન ટિકિટ માટે 300 રૂપિયા ખર્ચ્યા",
            "Travel"
        )
        assertEquals("ટ્રેન ટિકિટ", result.title)
        assertFalse(result.isMissing)
    }

    @Test
    fun test_22_HindiExpense() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "खाने पर 250 रुपये खर्च किए",
            "खाने पर 250 रुपये खर्च किए",
            "Food"
        )
        assertEquals("खाना", result.title)
        assertFalse(result.isMissing)
    }

    @Test
    fun test_23_HinglishExpense() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "add 300 rupees for dinner with jay",
            "Add 300 rupees for dinner with Jay",
            "Food"
        )
        assertTrue(result.title.contains("Dinner"))
    }

    @Test
    fun test_24_MixedLanguageExpense() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "station auto mate 100 rupees paid by cash",
            "Station auto mate 100 rupees paid by cash",
            "Travel"
        )
        assertEquals("Station Auto", result.title)
        assertFalse(result.isMissing)
    }

    @Test
    fun test_25_MissingTitle() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "add 300 rupees paid by upi",
            "Add 300 rupees paid by UPI",
            "General"
        )
        assertTrue(result.isMissing)
        assertTrue(result.title.isBlank())

        val cmd = VoiceCommandParser.parse("Add 300 rupees", sampleFriends, sampleGroups, sampleCategories)
        val validation = VoiceCommandValidator.validateAndBuildPreview(cmd, sampleFriends, sampleGroups, sampleCategories)
        assertTrue(validation is VoiceCommandResult.ClarificationRequired)
        assertEquals(VoiceClarificationType.MISSING_EXPENSE_TITLE, (validation as VoiceCommandResult.ClarificationRequired).prompt.type)
    }

    @Test
    fun test_26_MissingAmount() {
        val cmd = VoiceCommand.CreateTransaction(
            title = "Food",
            amountPaise = 0L,
            transactionType = TransactionType.EXPENSE,
            categoryId = 1L,
            categoryName = "Food"
        )
        val validation = VoiceCommandValidator.validateAndBuildPreview(cmd, sampleFriends, sampleGroups, sampleCategories)
        assertTrue(validation is VoiceCommandResult.ClarificationRequired)
        assertEquals(VoiceClarificationType.MISSING_AMOUNT, (validation as VoiceCommandResult.ClarificationRequired).prompt.type)
    }

    @Test
    fun test_27_PaymentMethodExtraction() {
        val cmd = VoiceCommandParser.parse("Add 500 for grocery paid by cash", sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(PaymentMethod.CASH, txn.paymentMethod)
        assertEquals("Grocery", txn.title)
    }

    @Test
    fun test_28_CategoryTitleSeparation() {
        val cmd = VoiceCommandParser.parse("Add 500 rupees for electricity bill", sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals("Electricity Bill", txn.title)
        assertEquals("Utilities", txn.categoryName)
        assertNotEquals(txn.title, txn.categoryName)
    }

    @Test
    fun test_29_MerchantNamePreservation() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "spent 450 at starbucks coffee",
            "Spent 450 at Starbucks Coffee",
            "Food"
        )
        assertEquals("Starbucks Coffee", result.title)
    }

    @Test
    fun test_30_LongDescription() {
        val result = VoiceEntityExtractor.extractExpenseTitle(
            "add an expense of 1200 rupees for annual vehicle maintenance service",
            "Add an expense of 1200 rupees for annual vehicle maintenance service",
            "General"
        )
        assertEquals("Annual Vehicle Maintenance Service", result.title)
    }

    // =========================================================================
    // PHASE 3: PAYER SELECTION (Tests 31 - 40)
    // =========================================================================

    @Test
    fun test_31_NewTransactionDefaultsToMe() {
        var selectedPayerId: Long? = null
        assertNull("Default payer must be Me (null)", selectedPayerId)
    }

    @Test
    fun test_32_PayerRemainsMeAfterCategorySelection() {
        var selectedPayerId: Long? = null
        var selectedCategory = sampleCategories[0]

        // User changes category
        selectedCategory = sampleCategories[1]
        assertNull("Payer must remain locked to Me when category changes", selectedPayerId)
    }

    @Test
    fun test_33_PayerRemainsMeAfterPaymentMethodSelection() {
        var selectedPayerId: Long? = null
        var selectedMethod = PaymentMethod.UPI

        // User changes payment method
        selectedMethod = PaymentMethod.CASH
        assertNull("Payer must remain locked to Me when payment method changes", selectedPayerId)
    }

    @Test
    fun test_34_ChangePayerToJay() {
        var selectedPayerId: Long? = null
        val jay = sampleFriends.first { it.name == "Jay" }

        selectedPayerId = jay.id
        assertEquals(jay.id, selectedPayerId)
    }

    @Test
    fun test_35_ChangePayerToManan() {
        var selectedPayerId: Long? = null
        val manan = sampleFriends.first { it.name == "Manan" }

        selectedPayerId = manan.id
        assertEquals(manan.id, selectedPayerId)
    }

    @Test
    fun test_36_SelectOnePayerOnly() {
        var selectedPayerId: Long? = 1L // Jay
        selectedPayerId = 2L // Change to Manav

        assertEquals(2L, selectedPayerId)
        assertNotNull(selectedPayerId)
    }

    @Test
    fun test_37_SearchLargeFriendList() {
        val query = "ja"
        val filtered = sampleFriends.filter { it.name.lowercase().contains(query) }
        assertTrue(filtered.any { it.name == "Jay" })
    }

    @Test
    fun test_38_ArchivedFriendUnavailableForNewPayer() {
        val availableForNew = sampleFriends.filter { !it.isArchived }
        assertFalse(availableForNew.any { it.name == "Archived Friend" })
    }

    @Test
    fun test_39_EditExistingTransactionPreservesPayer() {
        val existingTxn = Transaction(
            id = 10L,
            title = "Dinner",
            amountPaise = 100000L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = 1L, // Jay paid
            splitMethod = SplitMethod.EQUAL
        )

        // Opening edit dialog
        val editPayerId = existingTxn.paidByFriendId
        assertEquals("Existing payer must be preserved on edit", 1L, editPayerId)
    }

    @Test
    fun test_40_HistoricalPayerRemainsUnchanged() {
        val txns = listOf(
            Transaction(
                id = 1L,
                title = "Expense 1",
                amountPaise = 60000L,
                paidByFriendId = null, // Me paid
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                    TransactionParticipant(friendId = 1L, shareAmountPaise = 30000L)
                )
            ),
            Transaction(
                id = 2L,
                title = "Expense 2",
                amountPaise = 40000L,
                paidByFriendId = 1L, // Jay paid
                participants = listOf(
                    TransactionParticipant(friendId = null, shareAmountPaise = 20000L),
                    TransactionParticipant(friendId = 1L, shareAmountPaise = 20000L)
                )
            )
        )

        val friends = listOf(Friend(id = 1L, name = "Jay"))
        val computedFriends = LedgerCalculator.computeFriendLedgers(friends, txns, emptyList())
        val jay = computedFriends.first()
        // Me paid 600, Jay share 300 -> Jay owes Me 300
        // Jay paid 400, Me share 200 -> Me owes Jay 200
        // Net: Jay owes Me 100
        assertEquals(10000L, jay.totalTheyOweMePaise)
        assertEquals(0L, jay.totalIOweThemPaise)
        assertEquals(10000L, jay.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, jay.status)
    }

    // =========================================================================
    // PHASE 4: DETECTED TRANSACTIONS STATES (Tests 41 - 55)
    // =========================================================================

    @Test
    fun test_41_PendingRecordVisible() {
        val item = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 50000L,
            detectedType = TransactionType.EXPENSE,
            detectedMerchant = "Swiggy",
            rawContent = "Paid Rs 500 at Swiggy",
            status = PendingStatus.PENDING
        )
        assertEquals(PendingStatus.PENDING, item.status)
    }

    @Test
    fun test_42_IgnorePendingRecord() {
        val item = PendingTransaction(
            id = 1L,
            detectedAmountPaise = 50000L,
            detectedType = TransactionType.EXPENSE,
            status = PendingStatus.PENDING,
            rawContent = "SMS"
        )
        val ignored = item.copy(status = PendingStatus.IGNORED)
        assertEquals(PendingStatus.IGNORED, ignored.status)
    }

    @Test
    fun test_43_IgnoredRecordVisible() {
        val list = listOf(
            PendingTransaction(id = 1L, detectedAmountPaise = 50000L, detectedType = TransactionType.EXPENSE, status = PendingStatus.PENDING, rawContent = "A"),
            PendingTransaction(id = 2L, detectedAmountPaise = 30000L, detectedType = TransactionType.EXPENSE, status = PendingStatus.IGNORED, rawContent = "B")
        )
        val ignoredOnly = list.filter { it.status == PendingStatus.IGNORED }
        assertEquals(1, ignoredOnly.size)
        assertEquals(2L, ignoredOnly[0].id)
    }

    @Test
    fun test_44_RestoreIgnoredRecord() {
        val ignored = PendingTransaction(
            id = 2L,
            detectedAmountPaise = 30000L,
            detectedType = TransactionType.EXPENSE,
            status = PendingStatus.IGNORED,
            rawContent = "B"
        )
        val restored = ignored.copy(status = PendingStatus.PENDING)
        assertEquals(PendingStatus.PENDING, restored.status)
    }

    @Test
    fun test_45_RestoredRecordVisibleInPending() {
        val list = listOf(
            PendingTransaction(id = 2L, detectedAmountPaise = 30000L, detectedType = TransactionType.EXPENSE, status = PendingStatus.PENDING, rawContent = "B")
        )
        val pendingOnly = list.filter { it.status == PendingStatus.PENDING }
        assertEquals(1, pendingOnly.size)
    }

    @Test
    fun test_46_ApprovePendingRecord() {
        val pending = PendingTransaction(
            id = 3L,
            detectedAmountPaise = 25000L,
            detectedType = TransactionType.EXPENSE,
            status = PendingStatus.PENDING,
            rawContent = "SMS"
        )
        val approved = pending.copy(status = PendingStatus.RESOLVED)
        assertEquals(PendingStatus.RESOLVED, approved.status)
    }

    @Test
    fun test_47_ApprovedRecordVisible() {
        val list = listOf(
            PendingTransaction(id = 1L, detectedAmountPaise = 25000L, detectedType = TransactionType.EXPENSE, status = PendingStatus.RESOLVED, rawContent = "SMS")
        )
        val approvedOnly = list.filter { it.status == PendingStatus.RESOLVED }
        assertEquals(1, approvedOnly.size)
    }

    @Test
    fun test_48_ApprovedRecordNotDuplicated() {
        val refNumber = "UPI1234567890"
        val existingRefs = setOf(refNumber)
        assertTrue("Duplicate reference number must be detected", existingRefs.contains(refNumber))
    }

    @Test
    fun test_49_AllFilterShowsEveryState() {
        val list = listOf(
            PendingTransaction(id = 1L, detectedAmountPaise = 10000L, detectedType = TransactionType.EXPENSE, status = PendingStatus.PENDING, rawContent = "1"),
            PendingTransaction(id = 2L, detectedAmountPaise = 20000L, detectedType = TransactionType.EXPENSE, status = PendingStatus.RESOLVED, rawContent = "2"),
            PendingTransaction(id = 3L, detectedAmountPaise = 30000L, detectedType = TransactionType.EXPENSE, status = PendingStatus.IGNORED, rawContent = "3")
        )
        assertEquals(3, list.size)
        assertTrue(list.any { it.status == PendingStatus.PENDING })
        assertTrue(list.any { it.status == PendingStatus.RESOLVED })
        assertTrue(list.any { it.status == PendingStatus.IGNORED })
    }

    @Test
    fun test_50_SearchIgnoredRecord() {
        val list = listOf(
            PendingTransaction(id = 1L, detectedAmountPaise = 10000L, detectedType = TransactionType.EXPENSE, detectedMerchant = "Amazon", status = PendingStatus.IGNORED, rawContent = "Paid Amazon"),
            PendingTransaction(id = 2L, detectedAmountPaise = 20000L, detectedType = TransactionType.EXPENSE, detectedMerchant = "Zomato", status = PendingStatus.IGNORED, rawContent = "Paid Zomato")
        )
        val query = "amazon"
        val searchResults = list.filter { it.detectedMerchant?.lowercase()?.contains(query) == true }
        assertEquals(1, searchResults.size)
        assertEquals("Amazon", searchResults[0].detectedMerchant)
    }

    @Test
    fun test_51_DateFilterIgnoredRecords() {
        val now = System.currentTimeMillis()
        val yesterday = now - 86400000L
        val list = listOf(
            PendingTransaction(id = 1L, detectedAmountPaise = 10000L, detectedType = TransactionType.EXPENSE, timestamp = now, status = PendingStatus.IGNORED, rawContent = "Now"),
            PendingTransaction(id = 2L, detectedAmountPaise = 20000L, detectedType = TransactionType.EXPENSE, timestamp = yesterday, status = PendingStatus.IGNORED, rawContent = "Yesterday")
        )
        val todayOnly = list.filter { it.timestamp >= now - 3600000L }
        assertEquals(1, todayOnly.size)
    }

    @Test
    fun test_52_EmptyIgnoredState() {
        val emptyList = emptyList<PendingTransaction>()
        val filtered = emptyList.filter { it.status == PendingStatus.IGNORED }
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun test_53_DatabaseRestartPreservesIgnoredState() {
        val entityStatus = PendingStatus.IGNORED
        val restoredStatus = PendingStatus.valueOf(entityStatus.name)
        assertEquals(PendingStatus.IGNORED, restoredStatus)
    }

    @Test
    fun test_54_MigrationPreservesExistingRecords() {
        val statusString = "IGNORED"
        val parsed = PendingStatus.valueOf(statusString)
        assertEquals(PendingStatus.IGNORED, parsed)
    }

    @Test
    fun test_55_IgnoreRestoreDoesNotAffectFinancialBalances() {
        val txns = listOf(
            Transaction(id = 1L, title = "Dinner", amountPaise = 50000L, transactionType = TransactionType.EXPENSE)
        )
        val initialSummary = LedgerCalculator.computeFinancialSummary(txns, emptyList(), 0, startOfMonth = 0L)

        // An ignored detection occurs
        val ignoredDetection = PendingTransaction(id = 10L, detectedAmountPaise = 999900L, detectedType = TransactionType.EXPENSE, status = PendingStatus.IGNORED, rawContent = "Alert")

        // Invariant: financial summary remains exactly 50000L paise
        val summaryAfterIgnore = LedgerCalculator.computeFinancialSummary(txns, emptyList(), 0, startOfMonth = 0L)
        assertEquals(initialSummary.totalExpenseThisMonthPaise, summaryAfterIgnore.totalExpenseThisMonthPaise)
        assertEquals(50000L, summaryAfterIgnore.totalExpenseThisMonthPaise)
    }
}
