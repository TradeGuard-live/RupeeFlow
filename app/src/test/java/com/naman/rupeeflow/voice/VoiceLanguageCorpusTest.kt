package com.naman.rupeeflow.voice

import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.voice.model.*
import com.naman.rupeeflow.voice.nlp.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Large multilingual corpus test verifying parsing and intent recognition across:
 * 1. English (en-IN)
 * 2. Hindi (hi-IN) - Devanagari script
 * 3. Gujarati (gu-IN) - Gujarati script
 * 4. Hinglish (en-IN) - Romanized Hindi
 */
class VoiceLanguageCorpusTest {

    private lateinit var friends: List<Friend>
    private lateinit var groups: List<FriendGroup>
    private lateinit var categories: List<Category>

    @Before
    fun setup() {
        friends = listOf(
            Friend(id = 101L, name = "Jay"),
            Friend(id = 102L, name = "Manav"),
            Friend(id = 103L, name = "Priya"),
            Friend(id = 104L, name = "Rahul")
        )

        groups = listOf(
            FriendGroup(id = 1L, name = "College Trip", memberFriendIds = listOf(101L, 102L)),
            FriendGroup(id = 2L, name = "Flatmates", memberFriendIds = listOf(103L, 104L))
        )

        categories = listOf(
            Category(id = 1L, name = "Food & Dining", iconName = "restaurant", colorHex = "#EF4444"),
            Category(id = 2L, name = "Groceries", iconName = "shopping_cart", colorHex = "#10B981"),
            Category(id = 3L, name = "Transportation", iconName = "directions_car", colorHex = "#3B82F6"),
            Category(id = 4L, name = "Entertainment", iconName = "movie", colorHex = "#8B5CF6")
        )
    }

    // =========================================================================
    // 1. ENGLISH (en-IN) CORPUS TESTS
    // =========================================================================

    @Test
    fun test_English_SimpleExpenseCreation() {
        val input = "Add 300 for dinner"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(30000L, txn.amountPaise)
        assertTrue(txn.title.contains("dinner", ignoreCase = true))
        assertEquals(TransactionType.EXPENSE, txn.transactionType)
    }

    @Test
    fun test_English_ExpenseWithPaymentMethodCash() {
        val input = "Spent 150 on coffee via cash"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(15000L, txn.amountPaise)
        assertEquals(PaymentMethod.CASH, txn.paymentMethod)
    }

    @Test
    fun test_English_SharedEqualSplit() {
        val input = "Split 1200 for pizza with Jay and Manav equally"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(120000L, txn.amountPaise)
        assertEquals(SplitMethod.EQUAL, txn.splitMethod)
        // Should include Me (null), Jay (101), Manav (102)
        assertTrue(txn.participantFriendIds.contains(null))
        assertTrue(txn.participantFriendIds.contains(101L))
        assertTrue(txn.participantFriendIds.contains(102L))
    }

    @Test
    fun test_English_FriendPaidForSharedExpense() {
        val input = "Jay paid 600 for movie split with me"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(60000L, txn.amountPaise)
        assertEquals(101L, txn.paidByFriendId) // Jay paid
        assertEquals("Jay", txn.payerName)
    }

    @Test
    fun test_English_IncomeCreation() {
        val input = "Received 5000 salary via bank transfer"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(500000L, txn.amountPaise)
        assertEquals(TransactionType.INCOME, txn.transactionType)
    }

    @Test
    fun test_English_RefundCreation() {
        val input = "Got 450 refund from Amazon"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(45000L, txn.amountPaise)
        assertEquals(TransactionType.REFUND, txn.transactionType)
    }

    @Test
    fun test_English_SettlementWithFriend() {
        val input = "Settle 500 with Priya"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.CreateSettlement)
        val s = cmd as VoiceCommand.CreateSettlement
        assertEquals(50000L, s.amountPaise)
        assertEquals("Priya", s.friendName)
        assertEquals(103L, s.receiverId)
        assertNull(s.payerId) // Me paid Priya
    }

    @Test
    fun test_English_FriendSettledWithMe() {
        val input = "Priya returned 300 via UPI"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.CreateSettlement)
        val s = cmd as VoiceCommand.CreateSettlement
        assertEquals(30000L, s.amountPaise)
        assertEquals("Priya", s.friendName)
        assertEquals(103L, s.payerId) // Priya paid Me
        assertNull(s.receiverId)
    }

    @Test
    fun test_English_ReverseSettlement() {
        val input = "Reverse settlement with Rahul"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.ReverseSettlement)
        assertEquals("Rahul", (cmd as VoiceCommand.ReverseSettlement).friendName)
    }

    @Test
    fun test_English_DeleteTransaction() {
        val input = "Delete transaction for dinner"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.DeleteTransaction)
        assertEquals("dinner", (cmd as VoiceCommand.DeleteTransaction).searchCriteria?.description)
    }

    @Test
    fun test_English_QueryFriendBalance() {
        val input = "How much does Jay owe me?"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.Query)
        val q = cmd as VoiceCommand.Query
        assertEquals(VoiceIntent.QUERY_FRIEND_BALANCE, q.intent)
        assertEquals("Jay", q.friendName)
    }

    @Test
    fun test_English_QueryMonthlySpending() {
        val input = "Total spent this month"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.ENGLISH)
        assertTrue(cmd is VoiceCommand.Query)
        val q = cmd as VoiceCommand.Query
        assertEquals(VoiceIntent.QUERY_MONTHLY_SPENDING, q.intent)
        assertEquals(DateRangeFilter.THIS_MONTH, q.dateRange)
    }

    // =========================================================================
    // 2. HINDI (hi-IN) DEVANAGARI CORPUS TESTS
    // =========================================================================

    @Test
    fun test_Hindi_SimpleExpenseCreation() {
        val input = "रात के खाने के लिए 300 रुपये जोड़ें"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINDI)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(30000L, txn.amountPaise)
        assertEquals(TransactionType.EXPENSE, txn.transactionType)
    }

    @Test
    fun test_Hindi_ExpenseWithCash() {
        val input = "चाय के लिए 50 रुपये नकद खर्च किए"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINDI)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(5000L, txn.amountPaise)
        assertEquals(PaymentMethod.CASH, txn.paymentMethod)
    }

    @Test
    fun test_Hindi_SharedSplit() {
        val input = "जय और मानव के साथ 1200 रुपये बराबर बांटे"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINDI)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(120000L, txn.amountPaise)
        assertTrue(txn.participantFriendIds.contains(101L)) // Jay
        assertTrue(txn.participantFriendIds.contains(102L)) // Manav
    }

    @Test
    fun test_Hindi_FriendPaid() {
        val input = "जय ने दोपहर के भोजन के लिए 500 रुपये दिए"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINDI)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(50000L, txn.amountPaise)
        assertEquals(101L, txn.paidByFriendId)
    }

    @Test
    fun test_Hindi_Settlement() {
        val input = "प्रिया के साथ 500 रुपये का हिसाब चुकता करें"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINDI)
        assertTrue(cmd is VoiceCommand.CreateSettlement)
        val s = cmd as VoiceCommand.CreateSettlement
        assertEquals(50000L, s.amountPaise)
        assertEquals("Priya", s.friendName)
    }

    @Test
    fun test_Hindi_FriendBalanceQuery() {
        val input = "जय से मुझे कितने पैसे लेने हैं?"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINDI)
        assertTrue(cmd is VoiceCommand.Query)
        val q = cmd as VoiceCommand.Query
        assertEquals(VoiceIntent.QUERY_FRIEND_BALANCE, q.intent)
        assertEquals("Jay", q.friendName)
    }

    @Test
    fun test_Hindi_MonthlySpendingQuery() {
        val input = "इस महीने कुल कितना खर्च हुआ?"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINDI)
        assertTrue(cmd is VoiceCommand.Query)
        val q = cmd as VoiceCommand.Query
        assertEquals(VoiceIntent.QUERY_MONTHLY_SPENDING, q.intent)
    }

    // =========================================================================
    // 3. GUJARATI (gu-IN) GUJARATI SCRIPT CORPUS TESTS
    // =========================================================================

    @Test
    fun test_Gujarati_SimpleExpenseCreation() {
        val input = "ડિનર માટે 300 રૂપિયા ઉમેરો"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(30000L, txn.amountPaise)
        assertEquals(TransactionType.EXPENSE, txn.transactionType)
    }

    @Test
    fun test_Gujarati_ExpenseWithCash() {
        val input = "ચા માટે 50 રૂપિયા નકદ ખર્ચ્યા"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(5000L, txn.amountPaise)
        assertEquals(PaymentMethod.CASH, txn.paymentMethod)
    }

    @Test
    fun test_Gujarati_SharedSplit() {
        val input = "જય અને માનવ સાથે 1200 સરખે ભાગે વહેંચો"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(120000L, txn.amountPaise)
        assertTrue(txn.participantFriendIds.contains(101L))
        assertTrue(txn.participantFriendIds.contains(102L))
    }

    @Test
    fun test_Gujarati_FriendPaid() {
        val input = "જયે લંચ માટે 500 રૂપિયા ચૂકવ્યા"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(50000L, txn.amountPaise)
        assertEquals(101L, txn.paidByFriendId)
    }

    @Test
    fun test_Gujarati_Settlement() {
        val input = "પ્રિયા સાથે 500 રૂપિયા નો હિસાબ ચૂકતે કરો"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmd is VoiceCommand.CreateSettlement)
        val s = cmd as VoiceCommand.CreateSettlement
        assertEquals(50000L, s.amountPaise)
        assertEquals("Priya", s.friendName)
    }

    @Test
    fun test_Gujarati_FriendBalanceQuery() {
        val input = "જય પાસેથી મારે કેટલા લેવાના છે?"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmd is VoiceCommand.Query)
        val q = cmd as VoiceCommand.Query
        assertEquals(VoiceIntent.QUERY_FRIEND_BALANCE, q.intent)
        assertEquals("Jay", q.friendName)
    }

    @Test
    fun test_Gujarati_MonthlySpendingQuery() {
        val input = "આ મહિને કુલ કેટલો ખર્ચ થયો?"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmd is VoiceCommand.Query)
        val q = cmd as VoiceCommand.Query
        assertEquals(VoiceIntent.QUERY_MONTHLY_SPENDING, q.intent)
    }

    // =========================================================================
    // 4. HINGLISH CORPUS TESTS
    // =========================================================================

    @Test
    fun test_Hinglish_SimpleExpense() {
        val input = "Dinner ke liye 300 add karo"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(30000L, txn.amountPaise)
    }

    @Test
    fun test_Hinglish_ExpenseWithCash() {
        val input = "Chai ke 50 rupaye cash me diye"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(5000L, txn.amountPaise)
        assertEquals(PaymentMethod.CASH, txn.paymentMethod)
    }

    @Test
    fun test_Hinglish_SharedEqualSplit() {
        val input = "Jay aur Manav ke sath 1200 equal split karo"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(120000L, txn.amountPaise)
        assertTrue(txn.participantFriendIds.contains(101L))
        assertTrue(txn.participantFriendIds.contains(102L))
    }

    @Test
    fun test_Hinglish_FriendPaid() {
        val input = "Jay ne lunch ke liye 500 pay kiya"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINGLISH)
        assertTrue(cmd is VoiceCommand.CreateTransaction)
        val txn = cmd as VoiceCommand.CreateTransaction
        assertEquals(50000L, txn.amountPaise)
        assertEquals(101L, txn.paidByFriendId)
    }

    @Test
    fun test_Hinglish_FriendBalanceQuery() {
        val input = "Jay se kitne lene hain?"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINGLISH)
        assertTrue(cmd is VoiceCommand.Query)
        val q = cmd as VoiceCommand.Query
        assertEquals(VoiceIntent.QUERY_FRIEND_BALANCE, q.intent)
        assertEquals("Jay", q.friendName)
    }

    @Test
    fun test_Hinglish_MonthlySpendingQuery() {
        val input = "Is mahine total kitna spend hua?"
        val cmd = VoiceCommandParser.parse(input, friends, groups, categories, VoiceLanguage.HINGLISH)
        assertTrue(cmd is VoiceCommand.Query)
        val q = cmd as VoiceCommand.Query
        assertEquals(VoiceIntent.QUERY_MONTHLY_SPENDING, q.intent)
    }

    // =========================================================================
    // 5. NATURAL NUMBERS, QUANTIFIERS & WORD FORMS
    // =========================================================================

    @Test
    fun test_NumberParser_SpokenWordsAndQuantifiers() {
        assertEquals(30000L, VoiceNumberParser.parseAmountPaise("three hundred rupees"))
        assertEquals(500000L, VoiceNumberParser.parseAmountPaise("5k"))
        assertEquals(10000000L, VoiceNumberParser.parseAmountPaise("1 lakh"))
        assertEquals(25000000L, VoiceNumberParser.parseAmountPaise("2.5 lakh"))
        assertEquals(15000L, VoiceNumberParser.parseAmountPaise("dedh sau"))
        assertEquals(250000L, VoiceNumberParser.parseAmountPaise("dhai hazar"))
        assertEquals(30000L, VoiceNumberParser.parseAmountPaise("teen sau"))
        assertEquals(30000L, VoiceNumberParser.parseAmountPaise("tran so"))
    }

    @Test
    fun test_NumberParser_IndianNumerals() {
        // Devanagari numerals: ३०० -> 300
        assertEquals(30000L, VoiceNumberParser.parseAmountPaise("₹ ३००"))
        // Gujarati numerals: ૩૦૦ -> 300
        assertEquals(30000L, VoiceNumberParser.parseAmountPaise("₹ ૩૦૦"))
    }

    @Test
    fun test_DateParser_RelativeDates() {
        val today = VoiceDateParser.parseTransactionDate("Spent 100 today")
        val yesterday = VoiceDateParser.parseTransactionDate("Spent 100 yesterday")
        val kal = VoiceDateParser.parseTransactionDate("Spent 100 kal")
        val gaiKaale = VoiceDateParser.parseTransactionDate("100 gai kaale")

        assertTrue(yesterday < today)
        assertTrue(kal <= today)
        assertTrue(gaiKaale < today)
    }

    // =========================================================================
    // 6. PHASE 1 BUG REGRESSION & FRIEND MANAGEMENT TESTS
    // =========================================================================

    @Test
    fun test_Phase1Bug_AddFriend_MustNeverBeExpense() {
        // Core bug report: "Add friend" was erroneously classified as an expense titled "A new friend sukrut"
        val intent1 = VoiceIntentClassifier.classify("Add friend")
        assertEquals(VoiceIntent.FRIEND_CREATE, intent1)
        assertNotEquals(VoiceIntent.CREATE_EXPENSE, intent1)

        val intent2 = VoiceIntentClassifier.classify("Add friend Sukrut")
        assertEquals(VoiceIntent.FRIEND_CREATE, intent2)
        assertNotEquals(VoiceIntent.CREATE_EXPENSE, intent2)

        val intent3 = VoiceIntentClassifier.classify("A new friend sukrut")
        assertEquals(VoiceIntent.FRIEND_CREATE, intent3)
        assertNotEquals(VoiceIntent.CREATE_EXPENSE, intent3)

        val intent4 = VoiceIntentClassifier.classify("Create friend Pratik with phone number 9876543210")
        assertEquals(VoiceIntent.FRIEND_CREATE, intent4)
        assertNotEquals(VoiceIntent.CREATE_EXPENSE, intent4)
    }

    @Test
    fun test_FriendCreation_EntityExtraction() {
        val cmd1 = VoiceCommandParser.parse("Add friend Sukrut", friends, groups, categories)
        assertTrue(cmd1 is VoiceCommand.CreateFriend)
        val f1 = cmd1 as VoiceCommand.CreateFriend
        assertEquals("Sukrut", f1.name)
        assertNull(f1.phoneNumber)

        val cmd2 = VoiceCommandParser.parse("Create friend Pratik with phone number 9876543210", friends, groups, categories)
        assertTrue(cmd2 is VoiceCommand.CreateFriend)
        val f2 = cmd2 as VoiceCommand.CreateFriend
        assertEquals("Pratik", f2.name)
        assertEquals("9876543210", f2.phoneNumber)

        val cmd3 = VoiceCommandParser.parse("Add a friend named Jay", friends, groups, categories)
        assertTrue(cmd3 is VoiceCommand.CreateFriend)
        val f3 = cmd3 as VoiceCommand.CreateFriend
        assertEquals("Jay", f3.name)
    }

    @Test
    fun test_Gujarati_FriendCreation() {
        val intent = VoiceIntentClassifier.classify("જય નામનો મિત્ર ઉમેરો")
        assertEquals(VoiceIntent.FRIEND_CREATE, intent)

        val cmd = VoiceCommandParser.parse("જય નામનો મિત્ર ઉમેરો", friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmd is VoiceCommand.CreateFriend)
        val f = cmd as VoiceCommand.CreateFriend
        assertEquals("જય", f.name)

        val cmdPhone = VoiceCommandParser.parse("પ્રતિકનો ફોન નંબર 9876543210 સેવ કરો", friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmdPhone is VoiceCommand.CreateFriend)
        val fPhone = cmdPhone as VoiceCommand.CreateFriend
        assertEquals("પ્રતિક", fPhone.name)
        assertEquals("9876543210", fPhone.phoneNumber)
    }

    @Test
    fun test_Hindi_FriendCreation() {
        val intent = VoiceIntentClassifier.classify("जय नाम का नया दोस्त जोड़ो")
        assertEquals(VoiceIntent.FRIEND_CREATE, intent)

        val cmd = VoiceCommandParser.parse("जय नाम का नया दोस्त जोड़ो", friends, groups, categories, VoiceLanguage.HINDI)
        assertTrue(cmd is VoiceCommand.CreateFriend)
        val f = cmd as VoiceCommand.CreateFriend
        assertEquals("जय", f.name)

        val cmdPhone = VoiceCommandParser.parse("प्रतिक का नंबर 9876543210 सेव करो", friends, groups, categories, VoiceLanguage.HINDI)
        assertTrue(cmdPhone is VoiceCommand.CreateFriend)
        val fPhone = cmdPhone as VoiceCommand.CreateFriend
        assertEquals("प्रतिक", fPhone.name)
        assertEquals("9876543210", fPhone.phoneNumber)
    }

    @Test
    fun test_Friend_Deletion_And_Search() {
        val deleteCmd = VoiceCommandParser.parse("Delete friend Jay", friends, groups, categories)
        assertTrue(deleteCmd is VoiceCommand.DeleteFriend)
        val d = deleteCmd as VoiceCommand.DeleteFriend
        assertEquals(101L, d.friendId)
        assertEquals("Jay", d.friendName)

        val gujDelete = VoiceCommandParser.parse("જયને ફ્રેન્ડ લિસ્ટમાંથી કાઢી નાખો", friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(gujDelete is VoiceCommand.DeleteFriend)

        val searchCmd = VoiceCommandParser.parse("Search friend Jay", friends, groups, categories)
        assertTrue(searchCmd is VoiceCommand.SearchFriend)
        val s = searchCmd as VoiceCommand.SearchFriend
        assertEquals("jay", s.query.lowercase())
    }

    // =========================================================================
    // 7. GROUP MANAGEMENT TESTS
    // =========================================================================

    @Test
    fun test_GroupCreation_English_Hindi_Gujarati() {
        val cmdEn = VoiceCommandParser.parse("Create a group called Hostel Friends", friends, groups, categories)
        assertTrue(cmdEn is VoiceCommand.CreateGroup)
        val gEn = cmdEn as VoiceCommand.CreateGroup
        assertEquals("Hostel Friends", gEn.name)

        val cmdGu = VoiceCommandParser.parse("હોસ્ટેલ ફ્રેન્ડ્સ નામનું ગ્રુપ બનાવો", friends, groups, categories, VoiceLanguage.GUJARATI)
        assertTrue(cmdGu is VoiceCommand.CreateGroup)
        val gGu = cmdGu as VoiceCommand.CreateGroup
        assertEquals("હોસ્ટેલ ફ્રેન્ડ્સ", gGu.name)

        val cmdHi = VoiceCommandParser.parse("Hostel friends ka group banao", friends, groups, categories, VoiceLanguage.HINGLISH)
        assertTrue(cmdHi is VoiceCommand.CreateGroup)
        val gHi = cmdHi as VoiceCommand.CreateGroup
        assertEquals("Hostel Friends", gHi.name)
    }

    @Test
    fun test_Group_AddAndRemoveMember() {
        val addCmd = VoiceCommandParser.parse("Add Jay to College Trip", friends, groups, categories)
        assertTrue(addCmd is VoiceCommand.AddGroupMember)
        val a = addCmd as VoiceCommand.AddGroupMember
        assertEquals(1L, a.groupId)
        assertEquals("College Trip", a.groupName)
        assertTrue(a.friendNames.contains("Jay"))

        val removeCmd = VoiceCommandParser.parse("Remove Jay from College Trip", friends, groups, categories)
        assertTrue(removeCmd is VoiceCommand.RemoveGroupMember)
        val r = removeCmd as VoiceCommand.RemoveGroupMember
        assertEquals(1L, r.groupId)
        assertEquals("Jay", r.friendName)
    }

    @Test
    fun test_Group_Delete_And_List() {
        val delCmd = VoiceCommandParser.parse("Delete College Trip group", friends, groups, categories)
        assertTrue(delCmd is VoiceCommand.DeleteGroup)
        val d = delCmd as VoiceCommand.DeleteGroup
        assertEquals(1L, d.groupId)

        val listCmd = VoiceCommandParser.parse("Show all groups", friends, groups, categories)
        assertTrue(listCmd is VoiceCommand.ListGroups)
    }

    // =========================================================================
    // 8. HIGHEST EXPENSE & QUERY EXTENSIONS
    // =========================================================================

    @Test
    fun test_HighestExpenseQuery() {
        val intent1 = VoiceIntentClassifier.classify("Show my highest expense")
        assertEquals(VoiceIntent.QUERY_HIGHEST_EXPENSE, intent1)

        val intent2 = VoiceIntentClassifier.classify("Sabse bada kharch dikhao")
        assertEquals(VoiceIntent.QUERY_HIGHEST_EXPENSE, intent2)

        val intent3 = VoiceIntentClassifier.classify("સૌથી મોટો ખર્ચ બતાવો")
        assertEquals(VoiceIntent.QUERY_HIGHEST_EXPENSE, intent3)
    }

    // =========================================================================
    // 9. AUTOMATIC LANGUAGE DETECTION TESTS (PHASE 3)
    // =========================================================================

    @Test
    fun test_AutomaticLanguageDetection_NativeScripts() {
        // Native Gujarati
        assertEquals(VoiceLanguage.GUJARATI, VoiceLanguageDetector.detect("મિત્ર ઉમેરો"))
        assertEquals(VoiceLanguage.GUJARATI, VoiceLanguageDetector.detect("ત્રણસો રૂપિયાનો ખર્ચ ઉમેરો"))
        assertEquals(VoiceLanguage.GUJARATI, VoiceLanguageDetector.detect("જયને મેં પચાસ રૂપિયા આપ્યા"))
        assertEquals(VoiceLanguage.GUJARATI, VoiceLanguageDetector.detect("મારા હોસ્ટેલના મિત્રોનું ગ્રુપ બનાવો"))

        // Native Hindi
        assertEquals(VoiceLanguage.HINDI, VoiceLanguageDetector.detect("एक नया दोस्त जोड़ो"))
        assertEquals(VoiceLanguage.HINDI, VoiceLanguageDetector.detect("तीन सौ रुपये का खर्च जोड़ो"))
        assertEquals(VoiceLanguage.HINDI, VoiceLanguageDetector.detect("जय को मैंने पचास रुपये दिए"))
        assertEquals(VoiceLanguage.HINDI, VoiceLanguageDetector.detect("मेरे दोस्तों का ग्रुप बनाओ"))
    }

    @Test
    fun test_AutomaticLanguageDetection_EnglishAndMixed() {
        // Standard English
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguageDetector.detect("Add a friend named Jay"))
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguageDetector.detect("I spent 250 on food using cash"))
        assertEquals(VoiceLanguage.ENGLISH, VoiceLanguageDetector.detect("Create group Hostel Friends"))

        // Hinglish (Romanized Hindi)
        assertEquals(VoiceLanguage.HINGLISH, VoiceLanguageDetector.detect("Jay ko 500 diye"))
        assertEquals(VoiceLanguage.HINGLISH, VoiceLanguageDetector.detect("Hostel friends ka group banao"))
        assertEquals(VoiceLanguage.HINGLISH, VoiceLanguageDetector.detect("Is mahine kitna kharch hua"))
        assertEquals(VoiceLanguage.HINGLISH, VoiceLanguageDetector.detect("Add 500 rupees expense, cash se pay kiya"))

        // Code-switched Gujarati + English
        assertEquals(VoiceLanguage.GUJARATI, VoiceLanguageDetector.detect("મને 300 rupees નો expense add કરવો છે"))
        assertEquals(VoiceLanguage.GUJARATI, VoiceLanguageDetector.detect("Mara hostel group ma Jay ne add karo"))
        assertEquals(VoiceLanguage.GUJARATI, VoiceLanguageDetector.detect("500 rupees no kharch umero, Jay e pay karyo"))
    }
}

