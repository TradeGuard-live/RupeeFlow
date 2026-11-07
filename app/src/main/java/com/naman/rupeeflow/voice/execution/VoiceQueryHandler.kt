package com.naman.rupeeflow.voice.execution

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.voice.model.DateRangeFilter
import com.naman.rupeeflow.voice.model.VoiceActionPreview
import com.naman.rupeeflow.voice.model.VoiceCommand
import com.naman.rupeeflow.voice.model.VoiceIntent
import com.naman.rupeeflow.voice.model.VoiceLanguage
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Handles all voice financial queries safely using existing LedgerCalculator
 * and offline in-memory data representations.
 */
object VoiceQueryHandler {

    fun executeQuery(
        query: VoiceCommand.Query,
        transactions: List<Transaction>,
        friends: List<Friend>,
        settlements: List<Settlement>,
        groups: List<FriendGroup>,
        categories: List<Category>,
        language: VoiceLanguage = VoiceLanguage.ENGLISH
    ): VoiceActionPreview.QueryResultPreview {
        return when (query.intent) {
            VoiceIntent.QUERY_FRIEND_BALANCE -> handleFriendBalanceQuery(query, transactions, friends, settlements, language)
            VoiceIntent.QUERY_ALL_BALANCES -> handleAllBalancesQuery(transactions, friends, settlements, language)
            VoiceIntent.QUERY_GROUP_BALANCE -> handleGroupBalanceQuery(query, transactions, friends, settlements, groups, language)
            VoiceIntent.QUERY_MONTHLY_SPENDING,
            VoiceIntent.QUERY_TOTAL_SPENDING -> handleSpendingQuery(query, transactions, categories, language)
            VoiceIntent.QUERY_HIGHEST_EXPENSE -> handleHighestExpenseQuery(query, transactions, language)
            VoiceIntent.QUERY_CATEGORY_SPENDING -> handleCategorySpendingQuery(query, transactions, categories, language)
            VoiceIntent.QUERY_PAYMENT_METHOD_SPENDING -> handlePaymentMethodSpendingQuery(query, transactions, language)
            VoiceIntent.QUERY_TRANSACTION_DETAILS -> handleTransactionDetailsQuery(query, transactions, language)
            VoiceIntent.QUERY_SETTLEMENT_HISTORY -> handleSettlementHistoryQuery(query, settlements, friends, language)
            else -> VoiceActionPreview.QueryResultPreview(
                title = "Query Result",
                formattedAnswer = "No information found for this query.",
                spokenAnswer = "No information found for this query."
            )
        }
    }

    private fun handleFriendBalanceQuery(
        query: VoiceCommand.Query,
        transactions: List<Transaction>,
        friends: List<Friend>,
        settlements: List<Settlement>,
        language: VoiceLanguage
    ): VoiceActionPreview.QueryResultPreview {
        val targetFriend = if (query.friendId != null) {
            friends.firstOrNull { it.id == query.friendId }
        } else if (!query.friendName.isNullOrBlank()) {
            val q = query.friendName.trim().lowercase()
            friends.firstOrNull { it.name.trim().lowercase() == q }
                ?: friends.firstOrNull { it.name.trim().lowercase().contains(q) }
        } else {
            null
        }

        if (targetFriend == null) {
            val name = query.friendName ?: "Unknown friend"
            return VoiceActionPreview.QueryResultPreview(
                title = "Friend Not Found",
                formattedAnswer = "Could not find a friend named \"$name\".",
                spokenAnswer = "Could not find a friend named $name.",
                details = friends.take(5).map { it.name }
            )
        }

        val updatedFriends = LedgerCalculator.computeFriendLedgers(friends, transactions, settlements)
        val computedFriend = updatedFriends.firstOrNull { it.id == targetFriend.id } ?: targetFriend
        val (text, spoken) = VoiceResponseGenerator.friendBalanceResponse(computedFriend, language)

        val friendTransactions = transactions.filter { txn ->
            txn.transactionStatus == TransactionStatus.ACTIVE &&
                    (txn.paidByFriendId == computedFriend.id || txn.participants.any { it.friendId == computedFriend.id })
        }.take(3)

        val details = friendTransactions.map { txn ->
            "${txn.title}: ${CurrencyUtils.formatPaiseToRupees(txn.amountPaise)}"
        }

        return VoiceActionPreview.QueryResultPreview(
            title = "Balance with ${computedFriend.name}",
            formattedAnswer = text,
            spokenAnswer = spoken,
            details = details
        )
    }

    private fun handleAllBalancesQuery(
        transactions: List<Transaction>,
        friends: List<Friend>,
        settlements: List<Settlement>,
        language: VoiceLanguage
    ): VoiceActionPreview.QueryResultPreview {
        val updatedFriends = LedgerCalculator.computeFriendLedgers(friends, transactions, settlements)
        val activeFriends = updatedFriends.filter { !it.isArchived }

        val theyOweMe = activeFriends.filter { it.netBalancePaise > 0L }
        val iOweThem = activeFriends.filter { it.netBalancePaise < 0L }

        val totalTheyOwePaise = theyOweMe.sumOf { it.netBalancePaise }
        val totalIOwePaise = iOweThem.sumOf { abs(it.netBalancePaise) }

        val formattedTheyOwe = CurrencyUtils.formatPaiseToRupees(totalTheyOwePaise)
        val formattedIOwe = CurrencyUtils.formatPaiseToRupees(totalIOwePaise)

        val spokenTheyOwe = VoiceResponseGenerator.formatCurrencySpoken(totalTheyOwePaise, language)
        val spokenIOwe = VoiceResponseGenerator.formatCurrencySpoken(totalIOwePaise, language)

        val text = "You are owed $formattedTheyOwe from ${theyOweMe.size} friends. You owe $formattedIOwe to ${iOweThem.size} friends."
        val spoken = "You are owed $spokenTheyOwe, and you owe $spokenIOwe."

        val details = mutableListOf<String>()
        theyOweMe.forEach {
            details.add("${it.name} owes you ${CurrencyUtils.formatPaiseToRupees(it.netBalancePaise)}")
        }
        iOweThem.forEach {
            details.add("You owe ${it.name} ${CurrencyUtils.formatPaiseToRupees(abs(it.netBalancePaise))}")
        }

        return VoiceActionPreview.QueryResultPreview(
            title = "All Friend Balances",
            formattedAnswer = text,
            spokenAnswer = spoken,
            details = details
        )
    }

    private fun handleGroupBalanceQuery(
        query: VoiceCommand.Query,
        transactions: List<Transaction>,
        friends: List<Friend>,
        settlements: List<Settlement>,
        groups: List<FriendGroup>,
        language: VoiceLanguage
    ): VoiceActionPreview.QueryResultPreview {
        val targetGroup = if (query.groupId != null) {
            groups.firstOrNull { it.id == query.groupId }
        } else if (!query.groupName.isNullOrBlank()) {
            val q = query.groupName.trim().lowercase()
            groups.firstOrNull { it.name.trim().lowercase() == q }
                ?: groups.firstOrNull { it.name.trim().lowercase().contains(q) }
        } else {
            groups.firstOrNull()
        }

        if (targetGroup == null) {
            return VoiceActionPreview.QueryResultPreview(
                title = "Group Not Found",
                formattedAnswer = "No group matching \"${query.groupName ?: ""}\" found.",
                spokenAnswer = "Group not found."
            )
        }

        val result = LedgerCalculator.computeGroupLedger(
            groupId = targetGroup.id,
            groupMemberIds = targetGroup.memberFriendIds.toSet(),
            allFriends = friends,
            transactions = transactions,
            settlements = settlements
        )

        val net = result.netBalancePaise
        val absNet = abs(net)
        val netDisplay = CurrencyUtils.formatPaiseToRupees(absNet)
        val netSpoken = VoiceResponseGenerator.formatCurrencySpoken(absNet, language)

        val (answerText, answerSpoken) = when {
            net > 0L -> "In ${targetGroup.name}, you are owed $netDisplay." to "In ${targetGroup.name}, you are owed $netSpoken."
            net < 0L -> "In ${targetGroup.name}, you owe $netDisplay." to "In ${targetGroup.name}, you owe $netSpoken."
            else -> "In ${targetGroup.name}, your net balance is ₹0." to "In ${targetGroup.name}, your balance is settled up."
        }

        val details = result.friendsWithLedger.map { f ->
            val statusStr = if (f.netBalancePaise >= 0) "owes you ${CurrencyUtils.formatPaiseToRupees(f.netBalancePaise)}"
            else "you owe ${CurrencyUtils.formatPaiseToRupees(abs(f.netBalancePaise))}"
            "${f.name}: $statusStr"
        }

        return VoiceActionPreview.QueryResultPreview(
            title = "Group Ledger: ${targetGroup.name}",
            formattedAnswer = answerText,
            spokenAnswer = answerSpoken,
            details = details
        )
    }

    private fun handleSpendingQuery(
        query: VoiceCommand.Query,
        transactions: List<Transaction>,
        categories: List<Category>,
        language: VoiceLanguage
    ): VoiceActionPreview.QueryResultPreview {
        val (startTime, endTime, scopeTitle) = getDateBounds(query.dateRange)

        val activeExpenses = transactions.filter { txn ->
            txn.transactionStatus == TransactionStatus.ACTIVE &&
                    txn.transactionType == TransactionType.EXPENSE &&
                    txn.transactionDate in startTime..endTime
        }

        val totalExpensePaise = activeExpenses.sumOf { it.userExpensePaise }
        val (text, spoken) = VoiceResponseGenerator.spendingSummaryResponse(scopeTitle, totalExpensePaise, emptyMap(), language)

        val categorySpending = activeExpenses.groupBy { it.categoryId }.map { (catId, txns) ->
            val catName = categories.firstOrNull { it.id == catId }?.name ?: "Other"
            catName to txns.sumOf { it.userExpensePaise }
        }.sortedByDescending { it.second }

        val details = categorySpending.take(5).map { (name, amt) ->
            "$name: ${CurrencyUtils.formatPaiseToRupees(amt)}"
        }

        return VoiceActionPreview.QueryResultPreview(
            title = "Spending Summary ($scopeTitle)",
            formattedAnswer = text,
            spokenAnswer = spoken,
            details = details
        )
    }

    private fun handleHighestExpenseQuery(
        query: VoiceCommand.Query,
        transactions: List<Transaction>,
        language: VoiceLanguage
    ): VoiceActionPreview.QueryResultPreview {
        val (startTime, endTime, scopeTitle) = getDateBounds(query.dateRange)

        val activeExpenses = transactions.filter { txn ->
            txn.transactionStatus == TransactionStatus.ACTIVE &&
                    txn.transactionType == TransactionType.EXPENSE &&
                    txn.transactionDate in startTime..endTime
        }.sortedByDescending { it.amountPaise }

        val highest = activeExpenses.firstOrNull()
        if (highest == null) {
            return VoiceActionPreview.QueryResultPreview(
                title = "Highest Expense",
                formattedAnswer = "No expenses recorded for this period.",
                spokenAnswer = "No expenses recorded for this period."
            )
        }

        val (text, spoken) = VoiceResponseGenerator.highestExpenseResponse(
            highest.title,
            highest.amountPaise,
            language
        )

        val details = activeExpenses.take(3).map { txn ->
            "${txn.title}: ${CurrencyUtils.formatPaiseToRupees(txn.amountPaise)}"
        }

        return VoiceActionPreview.QueryResultPreview(
            title = "Highest Expense ($scopeTitle)",
            formattedAnswer = text,
            spokenAnswer = spoken,
            details = details
        )
    }

    private fun handleCategorySpendingQuery(
        query: VoiceCommand.Query,
        transactions: List<Transaction>,
        categories: List<Category>,
        language: VoiceLanguage
    ): VoiceActionPreview.QueryResultPreview {
        val catNameQuery = query.categoryName?.trim()?.lowercase() ?: ""
        val category = categories.firstOrNull { it.name.trim().lowercase() == catNameQuery }
            ?: categories.firstOrNull { it.name.trim().lowercase().contains(catNameQuery) }

        val (startTime, endTime, scopeTitle) = getDateBounds(query.dateRange)

        val matchingTransactions = transactions.filter { txn ->
            txn.transactionStatus == TransactionStatus.ACTIVE &&
                    txn.transactionType == TransactionType.EXPENSE &&
                    txn.transactionDate in startTime..endTime &&
                    (category != null && txn.categoryId == category.id || (txn.category?.name?.trim()?.lowercase()?.contains(catNameQuery) == true))
        }

        val categoryTitle = category?.name ?: query.categoryName ?: "Category"
        val totalPaise = matchingTransactions.sumOf { it.userExpensePaise }
        val (text, spoken) = VoiceResponseGenerator.spendingSummaryResponse("$categoryTitle ($scopeTitle)", totalPaise, emptyMap(), language)

        val details = matchingTransactions.take(5).map { txn ->
            "${txn.title}: ${CurrencyUtils.formatPaiseToRupees(txn.amountPaise)}"
        }

        return VoiceActionPreview.QueryResultPreview(
            title = "Category: $categoryTitle",
            formattedAnswer = text,
            spokenAnswer = spoken,
            details = details
        )
    }

    private fun handlePaymentMethodSpendingQuery(
        query: VoiceCommand.Query,
        transactions: List<Transaction>,
        language: VoiceLanguage
    ): VoiceActionPreview.QueryResultPreview {
        val pm = query.paymentMethod ?: PaymentMethod.UPI
        val (startTime, endTime, scopeTitle) = getDateBounds(query.dateRange)

        val matching = transactions.filter { txn ->
            txn.transactionStatus == TransactionStatus.ACTIVE &&
                    txn.transactionType == TransactionType.EXPENSE &&
                    txn.transactionDate in startTime..endTime &&
                    txn.paymentMethod == pm
        }

        val totalPaise = matching.sumOf { it.userExpensePaise }
        val (text, spoken) = VoiceResponseGenerator.spendingSummaryResponse("${pm.displayName} ($scopeTitle)", totalPaise, emptyMap(), language)

        val details = matching.take(5).map { txn ->
            "${txn.title}: ${CurrencyUtils.formatPaiseToRupees(txn.amountPaise)}"
        }

        return VoiceActionPreview.QueryResultPreview(
            title = "Payment Method: ${pm.displayName}",
            formattedAnswer = text,
            spokenAnswer = spoken,
            details = details
        )
    }

    private fun handleTransactionDetailsQuery(
        query: VoiceCommand.Query,
        transactions: List<Transaction>,
        language: VoiceLanguage
    ): VoiceActionPreview.QueryResultPreview {
        val active = transactions.filter { it.transactionStatus == TransactionStatus.ACTIVE }
            .sortedByDescending { it.transactionDate }

        val targetList = if (!query.categoryName.isNullOrBlank()) {
            active.filter { it.title.contains(query.categoryName, ignoreCase = true) || (it.category?.name?.contains(query.categoryName, ignoreCase = true) == true) }
        } else {
            active
        }.take(query.limit)

        if (targetList.isEmpty()) {
            return VoiceActionPreview.QueryResultPreview(
                title = "Recent Transactions",
                formattedAnswer = "No matching transactions found.",
                spokenAnswer = "No matching transactions found."
            )
        }

        val details = targetList.map { txn ->
            val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(txn.transactionDate))
            "$dateStr: ${txn.title} - ${CurrencyUtils.formatPaiseToRupees(txn.amountPaise)}"
        }

        return VoiceActionPreview.QueryResultPreview(
            title = "Recent Transactions",
            formattedAnswer = "Found ${targetList.size} transactions.",
            spokenAnswer = "Here are your recent transactions.",
            details = details
        )
    }

    private fun handleSettlementHistoryQuery(
        query: VoiceCommand.Query,
        settlements: List<Settlement>,
        friends: List<Friend>,
        language: VoiceLanguage
    ): VoiceActionPreview.QueryResultPreview {
        val targetSettlements = if (query.friendId != null) {
            settlements.filter { it.payerId == query.friendId || it.receiverId == query.friendId }
        } else if (!query.friendName.isNullOrBlank()) {
            val f = friends.firstOrNull { it.name.contains(query.friendName, ignoreCase = true) }
            if (f != null) {
                settlements.filter { it.payerId == f.id || it.receiverId == f.id }
            } else {
                settlements
            }
        } else {
            settlements
        }.sortedByDescending { it.settlementDate }.take(query.limit)

        if (targetSettlements.isEmpty()) {
            return VoiceActionPreview.QueryResultPreview(
                title = "Settlement History",
                formattedAnswer = "No settlements found.",
                spokenAnswer = "No settlements found."
            )
        }

        val details = targetSettlements.map { s ->
            val otherId = s.payerId ?: s.receiverId
            val fName = friends.firstOrNull { it.id == otherId }?.name ?: "Friend"
            val direction = if (s.receiverId == null) "Received from $fName" else "Paid to $fName"
            val status = if (s.isReversed) " (Reversed)" else ""
            val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(s.settlementDate))
            "$dateStr: $direction ${CurrencyUtils.formatPaiseToRupees(s.amountPaise)}$status"
        }

        return VoiceActionPreview.QueryResultPreview(
            title = "Settlement History",
            formattedAnswer = "Found ${targetSettlements.size} settlements.",
            spokenAnswer = "Here are your recent settlements.",
            details = details
        )
    }

    private fun getDateBounds(filter: DateRangeFilter): Triple<Long, Long, String> {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis

        return when (filter) {
            DateRangeFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Triple(cal.timeInMillis, now, "Today")
            }
            DateRangeFilter.YESTERDAY -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                Triple(start, cal.timeInMillis, "Yesterday")
            }
            DateRangeFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Triple(cal.timeInMillis, now, "This Week")
            }
            DateRangeFilter.LAST_WEEK -> {
                cal.add(Calendar.WEEK_OF_YEAR, -1)
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.DAY_OF_YEAR, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                Triple(start, cal.timeInMillis, "Last Week")
            }
            DateRangeFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Triple(cal.timeInMillis, now, "This Month")
            }
            DateRangeFilter.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                Triple(start, cal.timeInMillis, "Last Month")
            }
            DateRangeFilter.ALL_TIME -> Triple(0L, now, "All Time")
        }
    }
}
