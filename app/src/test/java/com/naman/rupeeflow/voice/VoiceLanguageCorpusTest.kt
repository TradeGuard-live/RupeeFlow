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
}
