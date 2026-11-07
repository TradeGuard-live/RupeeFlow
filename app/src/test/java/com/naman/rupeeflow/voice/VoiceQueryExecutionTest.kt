package com.naman.rupeeflow.voice

import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.voice.execution.VoiceQueryHandler
import com.naman.rupeeflow.voice.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VoiceQueryExecutionTest {

    private lateinit var friends: List<Friend>
    private lateinit var groups: List<FriendGroup>
    private lateinit var categories: List<Category>
    private lateinit var transactions: List<Transaction>
    private lateinit var settlements: List<Settlement>

    @Before
    fun setup() {
        friends = listOf(
            Friend(id = 101L, name = "Jay"),
            Friend(id = 102L, name = "Manav")
        )

        groups = listOf(
            FriendGroup(id = 1L, name = "College Trip", memberFriendIds = listOf(101L, 102L))
        )

        categories = listOf(
            Category(id = 1L, name = "Food & Dining", iconName = "restaurant", colorHex = "#EF4444"),
            Category(id = 2L, name = "Travel", iconName = "flight", colorHex = "#3B82F6")
        )

        // Me paid 600 for dinner with Jay -> Jay owes Me 300
        val p1 = listOf(
            TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
            TransactionParticipant(friendId = 101L, shareAmountPaise = 30000L)
        )
        val txn1 = Transaction(
            id = 1L,
            title = "Dinner with Jay",
            amountPaise = 60000L,
            paidByFriendId = null, // Me paid
            participants = p1,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis()
        )

        // Jay paid 400 for Taxi with Me -> Me owes Jay 200
        val p2 = listOf(
            TransactionParticipant(friendId = null, shareAmountPaise = 20000L),
            TransactionParticipant(friendId = 101L, shareAmountPaise = 20000L)
        )
        val txn2 = Transaction(
            id = 2L,
            title = "Taxi",
            amountPaise = 40000L,
            paidByFriendId = 101L, // Jay paid
            participants = p2,
            categoryId = 2L,
            transactionDate = System.currentTimeMillis()
        )

        transactions = listOf(txn1, txn2)
        settlements = emptyList()
    }

    @Test
    fun test_FriendBalanceQuery_JayOwesMeNet100() {
        // Net: Jay owes 300 - 200 = 100
        val query = VoiceCommand.Query(
            intent = VoiceIntent.QUERY_FRIEND_BALANCE,
            friendName = "Jay",
            friendId = 101L
        )

        val result = VoiceQueryHandler.executeQuery(
            query = query,
            transactions = transactions,
            friends = friends,
            settlements = settlements,
            groups = groups,
            categories = categories,
            language = VoiceLanguage.ENGLISH
        )

        assertEquals("Balance with Jay", result.title)
        assertTrue(result.formattedAnswer.contains("100"))
        assertTrue(result.spokenAnswer.contains("100"))
        assertTrue(result.formattedAnswer.contains("owes you", ignoreCase = true))
    }

    @Test
    fun test_CategorySpendingQuery_Food() {
        val query = VoiceCommand.Query(
            intent = VoiceIntent.QUERY_CATEGORY_SPENDING,
            categoryName = "Food & Dining",
            dateRange = DateRangeFilter.THIS_MONTH
        )

        val result = VoiceQueryHandler.executeQuery(
            query = query,
            transactions = transactions,
            friends = friends,
            settlements = settlements,
            groups = groups,
            categories = categories,
            language = VoiceLanguage.ENGLISH
        )

        assertTrue(result.title.contains("Food"))
        // User share in Food is ₹300 (30000 paise)
        assertTrue(result.formattedAnswer.contains("300"))
    }

    @Test
    fun test_GroupLedgerQuery() {
        val query = VoiceCommand.Query(
            intent = VoiceIntent.QUERY_GROUP_BALANCE,
            groupName = "College Trip",
            groupId = 1L
        )

        val result = VoiceQueryHandler.executeQuery(
            query = query,
            transactions = transactions,
            friends = friends,
            settlements = settlements,
            groups = groups,
            categories = categories,
            language = VoiceLanguage.ENGLISH
        )

        assertTrue(result.title.contains("College Trip"))
    }

    @Test
    fun test_SettlementHistoryQuery_EmptyAndPopulated() {
        val emptyQuery = VoiceCommand.Query(intent = VoiceIntent.QUERY_SETTLEMENT_HISTORY)
        val emptyResult = VoiceQueryHandler.executeQuery(
            query = emptyQuery,
            transactions = transactions,
            friends = friends,
            settlements = emptyList(),
            groups = groups,
            categories = categories,
            language = VoiceLanguage.ENGLISH
        )
        assertEquals("No settlements found.", emptyResult.formattedAnswer)

        val populatedSettlements = listOf(
            Settlement(
                id = 1L,
                payerId = 101L, // Jay paid Me
                receiverId = null,
                amountPaise = 10000L,
                settlementDate = System.currentTimeMillis()
            )
        )
        val populatedResult = VoiceQueryHandler.executeQuery(
            query = emptyQuery,
            transactions = transactions,
            friends = friends,
            settlements = populatedSettlements,
            groups = groups,
            categories = categories,
            language = VoiceLanguage.ENGLISH
        )
        assertTrue(populatedResult.formattedAnswer.contains("1 settlement"))
        assertEquals(1, populatedResult.details.size)
    }
}

