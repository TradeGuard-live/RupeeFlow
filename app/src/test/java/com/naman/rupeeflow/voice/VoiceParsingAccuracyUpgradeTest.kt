package com.naman.rupeeflow.voice

import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.voice.execution.VoiceCommandValidator
import com.naman.rupeeflow.voice.model.*
import com.naman.rupeeflow.voice.nlp.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 20-test suite validating voice entity extraction, typed drafts, and clarification logic (Tests 51 - 70).
 */
class VoiceParsingAccuracyUpgradeTest {

    private lateinit var sampleFriends: List<Friend>
    private lateinit var sampleGroups: List<FriendGroup>
    private lateinit var sampleCategories: List<Category>

    @Before
    fun setup() {
        sampleFriends = listOf(
            Friend(id = 1L, name = "Jay", phoneNumber = "9033117004"),
            Friend(id = 2L, name = "Manav", phoneNumber = "7574848491"),
            Friend(id = 3L, name = "Sukrut", phoneNumber = "9876543210"),
            Friend(id = 4L, name = "Raj Kumar", phoneNumber = "9898011223")
        )

        sampleGroups = listOf(
            FriendGroup(id = 1L, name = "Hostel Friends", memberFriendIds = listOf(1L, 2L, 3L))
        )

        sampleCategories = listOf(
            Category(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF5722"),
            Category(id = 2L, name = "Travel", iconName = "flight", colorHex = "#2196F3"),
            Category(id = 3L, name = "Utilities", iconName = "bolt", colorHex = "#FFC107"),
            Category(id = 4L, name = "General", iconName = "category", colorHex = "#607D8B")
        )
    }

    // =========================================================================
    // VOICE FRIEND EXTRACTION (Tests 51 - 56)
    // =========================================================================

    @Test
    fun test_51_AddFriendSukrut() {
        val result = VoiceEntityExtractor.extractFriendName("add friend sukrut", "Add friend Sukrut")
        assertEquals("Sukrut", result.name)
        assertFalse(result.isAmbiguousOrMissing)

        val cmd = VoiceCommandParser.parse("Add friend Sukrut", sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd is VoiceCommand.CreateFriend)
        assertEquals("Sukrut", (cmd as VoiceCommand.CreateFriend).name)
    }

    @Test
    fun test_52_AddFriendRajKumar() {
        val result = VoiceEntityExtractor.extractFriendName("add friend raj kumar", "Add friend Raj Kumar")
        assertEquals("Raj Kumar", result.name)
        assertFalse(result.isAmbiguousOrMissing)
    }

    @Test
    fun test_53_GujaratiFriendCommand() {
        val res1 = VoiceEntityExtractor.extractFriendName("મિત્ર સુકૃતને ઉમેરો", "મિત્ર સુકૃતને ઉમેરો")
        assertEquals("સુકૃત", res1.name)

        val res2 = VoiceEntityExtractor.extractFriendName("સુકૃત નામનો મિત્ર ઉમેરો", "સુકૃત નામનો મિત્ર ઉમેરો")
        assertEquals("સુકૃત", res2.name)
    }

    @Test
    fun test_54_HindiFriendCommand() {
        val res1 = VoiceEntityExtractor.extractFriendName("एक दोस्त सुकृत जोड़ो", "एक दोस्त सुकृत जोड़ो")
        assertEquals("सुकृत", res1.name)

        val res2 = VoiceEntityExtractor.extractFriendName("सुकृत नाम का दोस्त जोड़ो", "सुकृत नाम का दोस्त जोड़ो")
        assertEquals("सुकृत", res2.name)
    }

    @Test
    fun test_55_EnglishFriendCommand() {
        val res1 = VoiceEntityExtractor.extractFriendName("add a friend sukrut", "Add a friend Sukrut")
        assertEquals("Sukrut", res1.name)

        val res2 = VoiceEntityExtractor.extractFriendName("add friend named jay", "Add friend named Jay")
        assertEquals("Jay", res2.name)
    }

    @Test
    fun test_56_MixedLanguageFriendCommand() {
        val res1 = VoiceEntityExtractor.extractFriendName("sukrut ne friend list ma add karo", "Sukrut ne friend list ma add karo")
        assertEquals("Sukrut", res1.name)

        val res2 = VoiceEntityExtractor.extractFriendName("jay ko friend list mein add karo", "Jay ko friend list mein add karo")
        assertEquals("Jay", res2.name)
    }

    // =========================================================================
    // VOICE EXPENSE EXTRACTION & DRAFTS (Tests 57 - 64)
    // =========================================================================

    @Test
    fun test_57_TrainTicketExpense() {
        val raw = "Add an expense of 300 rupees for train ticket."
        val titleRes = VoiceEntityExtractor.extractExpenseTitle(VoiceTextNormalizer.normalize(raw), raw, "Travel")
        assertEquals("Train Ticket", titleRes.title)
        assertFalse(titleRes.isMissing)

        val cmd = VoiceCommandParser.parse(raw, sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val createTxn = cmd as VoiceCommand.CreateTransaction
        assertEquals("Train Ticket", createTxn.title)
        assertEquals(30000L, createTxn.amountPaise)
        assertNotNull(createTxn.draft)
        assertEquals("Train Ticket", createTxn.draft?.title)
        assertEquals(30000L, createTxn.draft?.amountPaise)
    }

    @Test
    fun test_58_FoodExpense() {
        val raw = "I spent 200 rupees on food using cash."
        val titleRes = VoiceEntityExtractor.extractExpenseTitle(VoiceTextNormalizer.normalize(raw), raw, "Food")
        assertEquals("Food", titleRes.title)

        val cmd = VoiceCommandParser.parse(raw, sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val createTxn = cmd as VoiceCommand.CreateTransaction
        assertEquals("Food", createTxn.title)
        assertEquals(20000L, createTxn.amountPaise)
        assertEquals(PaymentMethod.CASH, createTxn.paymentMethod)
    }

    @Test
    fun test_59_GujaratiExpenseDescription() {
        val raw = "આજે ટ્રેન ટિકિટ માટે 300 રૂપિયા ખર્ચ્યા."
        val titleRes = VoiceEntityExtractor.extractExpenseTitle(VoiceTextNormalizer.normalize(raw), raw, "Travel")
        assertEquals("ટ્રેન ટિકિટ", titleRes.title)
        assertFalse(titleRes.isMissing)

        val cmd = VoiceCommandParser.parse(raw, sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val createTxn = cmd as VoiceCommand.CreateTransaction
        assertEquals("ટ્રેન ટિકિટ", createTxn.title)
        assertEquals(30000L, createTxn.amountPaise)
    }

    @Test
    fun test_60_HindiExpenseDescription() {
        val raw = "खाने पर 250 रुपये खर्च किए।"
        val titleRes = VoiceEntityExtractor.extractExpenseTitle(VoiceTextNormalizer.normalize(raw), raw, "Food")
        assertEquals("खाना", titleRes.title)

        val cmd = VoiceCommandParser.parse(raw, sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val createTxn = cmd as VoiceCommand.CreateTransaction
        assertEquals("खाना", createTxn.title)
        assertEquals(25000L, createTxn.amountPaise)
    }

    @Test
    fun test_61_AmountExtraction() {
        assertEquals(50000L, VoiceNumberParser.parseAmountPaise("500 rupees"))
        assertEquals(125000L, VoiceNumberParser.parseAmountPaise("1250 rupees"))
        assertEquals(25050L, VoiceNumberParser.parseAmountPaise("250.50 rupees"))
    }

    @Test
    fun test_62_CategoryExtraction() {
        val cat = VoiceEntityResolver.resolveCategory("paid 300 for flight ticket", sampleCategories)
        assertEquals("Travel", cat.name)
    }

    @Test
    fun test_63_MerchantExtraction() {
        val titleRes = VoiceEntityExtractor.extractExpenseTitle("paid 450 at Starbucks", "paid 450 at Starbucks", "Food")
        assertEquals("Starbucks", titleRes.title)
    }

    @Test
    fun test_64_PaymentMethodExtraction() {
        assertEquals(PaymentMethod.CASH, VoiceEntityResolver.resolvePaymentMethod("paid 100 in cash"))
        assertEquals(PaymentMethod.UPI, VoiceEntityResolver.resolvePaymentMethod("paid 100 via upi"))
        assertEquals(PaymentMethod.CREDIT_CARD, VoiceEntityResolver.resolvePaymentMethod("using credit card"))
    }

    // =========================================================================
    // CLARIFICATION & ROBUSTNESS (Tests 65 - 70)
    // =========================================================================

    @Test
    fun test_65_MissingTitleTriggersClarification() {
        val cmd = VoiceCommand.CreateTransaction(
            title = "",
            amountPaise = 30000L,
            categoryId = 1L
        )
        val result = VoiceCommandValidator.validateAndBuildPreview(cmd, sampleFriends, sampleGroups, sampleCategories)
        assertTrue(result is VoiceCommandResult.ClarificationRequired)
        assertEquals(VoiceClarificationType.MISSING_EXPENSE_TITLE, (result as VoiceCommandResult.ClarificationRequired).prompt.type)
    }

    @Test
    fun test_66_AmbiguousNameTriggersClarification() {
        val cmd = VoiceCommand.CreateFriend(name = "")
        val result = VoiceCommandValidator.validateAndBuildPreview(cmd, sampleFriends, sampleGroups, sampleCategories)
        assertTrue(result is VoiceCommandResult.ClarificationRequired)
        assertEquals(VoiceClarificationType.MISSING_FRIEND_NAME, (result as VoiceCommandResult.ClarificationRequired).prompt.type)
    }

    @Test
    fun test_67_MultiplePossibleIntents() {
        // Unclear intent without amount should return Unknown
        val cmd = VoiceCommandParser.parse("something strange", sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd is VoiceCommand.Unknown)
    }

    @Test
    fun test_68_SpeechRecognitionTypoNormalization() {
        val norm = VoiceTextNormalizer.normalize("Ruppees 250 for  Food!!!")
        assertTrue(norm.contains("250"))
        assertTrue(norm.contains("food"))
    }

    @Test
    fun test_69_ContextAwareCorrection() {
        val suspicious = VoiceEntityExtractor.detectSuspiciousFriendNames(
            listOf(Friend(id = 1L, name = "Add Friend Sukrut"))
        )
        assertEquals(1, suspicious.size)
        assertEquals("Sukrut", suspicious[0].second)
    }

    @Test
    fun test_70_DuplicateVoiceCommandProtection() {
        val cmd1 = VoiceCommandParser.parse("Add 200 for food", sampleFriends, sampleGroups, sampleCategories)
        val cmd2 = VoiceCommandParser.parse("Add 200 for food", sampleFriends, sampleGroups, sampleCategories)
        assertTrue(cmd1 is VoiceCommand.CreateTransaction)
        assertTrue(cmd2 is VoiceCommand.CreateTransaction)
        val t1 = cmd1 as VoiceCommand.CreateTransaction
        val t2 = cmd2 as VoiceCommand.CreateTransaction
        assertEquals(t1.title, t2.title)
        assertEquals(t1.amountPaise, t2.amountPaise)
        assertEquals(t1.categoryId, t2.categoryId)
        assertEquals(t1.paymentMethod, t2.paymentMethod)
    }
}
