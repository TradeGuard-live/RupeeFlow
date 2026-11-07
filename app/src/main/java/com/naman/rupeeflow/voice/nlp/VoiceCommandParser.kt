package com.naman.rupeeflow.voice.nlp

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.voice.model.*

/**
 * Parses natural language input into typed VoiceCommand objects.
 */
object VoiceCommandParser {

    fun parse(
        text: String,
        friends: List<Friend>,
        groups: List<FriendGroup>,
        categories: List<Category>,
        language: VoiceLanguage = VoiceLanguage.ENGLISH
    ): VoiceCommand {
        val normalized = VoiceTextNormalizer.normalize(text)
        val intent = VoiceIntentClassifier.classify(normalized)

        return when (intent) {
            VoiceIntent.CONFIRM -> VoiceCommand.Confirm
            VoiceIntent.CANCEL -> VoiceCommand.Cancel
            VoiceIntent.HELP -> VoiceCommand.Help

            VoiceIntent.CREATE_SETTLEMENT -> parseSettlementCreation(normalized, friends)
            VoiceIntent.EDIT_SETTLEMENT -> parseSettlementEdit(normalized, friends)
            VoiceIntent.REVERSE_SETTLEMENT -> parseSettlementReverse(normalized, friends)
            VoiceIntent.DELETE_SETTLEMENT -> parseSettlementDelete(normalized, friends)

            VoiceIntent.EDIT_TRANSACTION -> parseTransactionEdit(normalized, friends, categories)
            VoiceIntent.DELETE_TRANSACTION -> parseTransactionDelete(normalized)

            VoiceIntent.QUERY_FRIEND_BALANCE,
            VoiceIntent.QUERY_ALL_BALANCES,
            VoiceIntent.QUERY_GROUP_BALANCE,
            VoiceIntent.QUERY_TOTAL_SPENDING,
            VoiceIntent.QUERY_MONTHLY_SPENDING,
            VoiceIntent.QUERY_CATEGORY_SPENDING,
            VoiceIntent.QUERY_PAYMENT_METHOD_SPENDING,
            VoiceIntent.QUERY_TRANSACTION_DETAILS,
            VoiceIntent.QUERY_SETTLEMENT_HISTORY -> parseQuery(normalized, intent, friends, groups, categories)

            VoiceIntent.CREATE_INCOME -> parseTransactionCreation(normalized, TransactionType.INCOME, friends, groups, categories)
            VoiceIntent.CREATE_REFUND -> parseTransactionCreation(normalized, TransactionType.REFUND, friends, groups, categories)
            VoiceIntent.CREATE_TRANSFER -> parseTransactionCreation(normalized, TransactionType.TRANSFER, friends, groups, categories)
            VoiceIntent.CREATE_EXPENSE -> parseTransactionCreation(normalized, TransactionType.EXPENSE, friends, groups, categories)

            VoiceIntent.UNKNOWN_INTENT -> {
                // If there's an amount, try treating as an expense
                val amount = VoiceNumberParser.parseAmountPaise(normalized)
                if (amount != null && amount > 0L) {
                    parseTransactionCreation(normalized, TransactionType.EXPENSE, friends, groups, categories)
                } else {
                    VoiceCommand.Unknown(text)
                }
            }
            else -> VoiceCommand.Unknown(text)
        }
    }

    private fun parseTransactionCreation(
        text: String,
        type: TransactionType,
        friends: List<Friend>,
        groups: List<FriendGroup>,
        categories: List<Category>
    ): VoiceCommand {
        val amount = VoiceNumberParser.parseAmountPaise(text) ?: 0L
        val date = VoiceDateParser.parseTransactionDate(text)
        val paymentMethod = VoiceEntityResolver.resolvePaymentMethod(text)
        val category = VoiceEntityResolver.resolveCategory(text, categories)

        val referencedFriends = VoiceEntityResolver.findReferencedFriends(text, friends)
        val referencedGroup = VoiceEntityResolver.findReferencedGroup(text, groups)
        val (payerId, payerName) = VoiceEntityResolver.resolvePayer(text, referencedFriends)

        // Description extraction
        val description = extractTransactionTitle(text, category.name)

        // Participants and Split resolution
        val participantIds = mutableListOf<Long?>()
        var splitMethod = SplitMethod.EQUAL
        val customShares = mutableMapOf<Long?, Long>()
        val customPercentages = mutableMapOf<Long?, Double>()

        if (referencedGroup != null) {
            // Group split: all members of group + Me if payer is Me
            for (fId in referencedGroup.memberFriendIds) {
                if (!participantIds.contains(fId)) participantIds.add(fId)
            }
            if (payerId == null && !participantIds.contains(null)) {
                participantIds.add(null) // Include ME
            }
        } else if (referencedFriends.isNotEmpty()) {
            // Check for percentage mentions: e.g. "50 percent with Jay and 50 percent with Manav"
            val pctRegex = Regex("""(\d+(?:\.\d+)?)\s*(?:percent|%|pratishat|ટકા|प्रतिशत)""")
            val pctMatches = pctRegex.findAll(text).toList()

            if (pctMatches.isNotEmpty() && pctMatches.size == referencedFriends.size) {
                splitMethod = SplitMethod.PERCENTAGE
                for (i in referencedFriends.indices) {
                    val pct = pctMatches[i].groupValues[1].toDoubleOrNull() ?: 0.0
                    val f = referencedFriends[i]
                    participantIds.add(f.id)
                    customPercentages[f.id] = pct
                }
            } else {
                // Check for custom share mentions: "My share 400, Jay 300, Manav 300" or "My share is 300"
                val myShareMatch = Regex("""(?:my share|mera hissa|maro bhag|મારો ભાગ|मेरा हिस्सा)(?:\s*(?:is|hai|che|છે|है))?\s*(\d+)""").find(text)
                if (myShareMatch != null && amount > 0L) {
                    splitMethod = SplitMethod.EXACT_AMOUNT
                    val mySharePaise = (myShareMatch.groupValues[1].toLongOrNull() ?: 0L) * 100L
                    customShares[null] = mySharePaise
                    participantIds.add(null)

                    // Distribute remaining among referenced friends
                    val remaining = (amount - mySharePaise).coerceAtLeast(0L)
                    if (referencedFriends.isNotEmpty()) {
                        val sharePerFriend = remaining / referencedFriends.size
                        for (f in referencedFriends) {
                            customShares[f.id] = sharePerFriend
                            participantIds.add(f.id)
                        }
                    }
                } else {
                    // Standard Equal Split between payer and all referenced friends
                    splitMethod = SplitMethod.EQUAL
                    if (!participantIds.contains(null) && (text.contains(" me") || text.contains(" me,") || text.contains("between me") || payerId == null)) {
                        participantIds.add(null) // Include ME
                    }
                    for (f in referencedFriends) {
                        if (!participantIds.contains(f.id)) participantIds.add(f.id)
                    }
                }
            }
        }

        return VoiceCommand.CreateTransaction(
            title = description,
            amountPaise = amount,
            transactionType = type,
            categoryId = category.id,
            categoryName = category.name,
            paidByFriendId = payerId,
            payerName = payerName,
            participantFriendIds = participantIds,
            splitMethod = splitMethod,
            customSharesPaise = customShares,
            customPercentages = customPercentages,
            paymentMethod = paymentMethod,
            transactionDate = date,
            groupId = referencedGroup?.id,
            groupName = referencedGroup?.name,
            rawInput = text
        )
    }

    private fun parseSettlementCreation(text: String, friends: List<Friend>): VoiceCommand {
        val amount = VoiceNumberParser.parseAmountPaise(text) ?: 0L
        val paymentMethod = VoiceEntityResolver.resolvePaymentMethod(text)
        val date = VoiceDateParser.parseTransactionDate(text)

        val referencedFriends = VoiceEntityResolver.findReferencedFriends(text, friends)
        val targetFriend = referencedFriends.firstOrNull()

        // Direction:
        // Did I pay the friend, or did the friend pay me?
        val friendPaidMe = text.contains("returned") ||
                text.contains("received") ||
                text.contains("malya") ||
                text.contains("paasethi") ||
                text.contains("se mile") ||
                text.contains("lautaye") ||
                text.contains("મળ્યા") ||
                text.contains("પાસેથી") ||
                text.contains("मिले") ||
                text.contains("लौटाए")

        val (payerId, receiverId) = if (friendPaidMe) {
            Pair(targetFriend?.id, null) // Friend paid ME
        } else {
            Pair(null, targetFriend?.id) // ME paid Friend
        }

        return VoiceCommand.CreateSettlement(
            amountPaise = amount,
            payerId = payerId,
            receiverId = receiverId,
            friendName = targetFriend?.name ?: "",
            paymentMethod = paymentMethod,
            date = date,
            notes = "Recorded via Voice Assistant",
            rawInput = text
        )
    }

    private fun parseSettlementEdit(text: String, friends: List<Friend>): VoiceCommand {
        val newAmount = VoiceNumberParser.parseAmountPaise(text)
        val newPaymentMethod = if (text.contains("cash") || text.contains("upi") || text.contains("card")) {
            VoiceEntityResolver.resolvePaymentMethod(text)
        } else null
        val referencedFriends = VoiceEntityResolver.findReferencedFriends(text, friends)

        return VoiceCommand.EditSettlement(
            friendName = referencedFriends.firstOrNull()?.name,
            newAmountPaise = newAmount,
            newPaymentMethod = newPaymentMethod,
            rawInput = text
        )
    }

    private fun parseSettlementReverse(text: String, friends: List<Friend>): VoiceCommand {
        val referencedFriends = VoiceEntityResolver.findReferencedFriends(text, friends)
        return VoiceCommand.ReverseSettlement(
            friendName = referencedFriends.firstOrNull()?.name,
            rawInput = text
        )
    }

    private fun parseSettlementDelete(text: String, friends: List<Friend>): VoiceCommand {
        val referencedFriends = VoiceEntityResolver.findReferencedFriends(text, friends)
        return VoiceCommand.DeleteSettlement(
            friendName = referencedFriends.firstOrNull()?.name,
            rawInput = text
        )
    }

    private fun parseTransactionEdit(
        text: String,
        friends: List<Friend>,
        categories: List<Category>
    ): VoiceCommand {
        // e.g. "Change my train ticket expense to 350"
        // e.g. "Change the payment method to UPI"
        // e.g. "Change Jay's share to 200"
        val newAmount = VoiceNumberParser.parseAmountPaise(text)
        val newPaymentMethod = if (text.contains("cash") || text.contains("upi") || text.contains("card") || text.contains("net banking")) {
            VoiceEntityResolver.resolvePaymentMethod(text)
        } else null

        val descriptionKeywords = listOf("train", "ticket", "dinner", "lunch", "auto", "movie", "grocery", "food", "hotel", "rent")
        val desc = descriptionKeywords.firstOrNull { text.contains(it) }

        return VoiceCommand.EditTransaction(
            searchCriteria = TransactionSearchCriteria(
                description = desc,
                dateFilter = VoiceDateParser.parseDateRangeFilter(text)
            ),
            newAmountPaise = newAmount,
            newPaymentMethod = newPaymentMethod,
            rawInput = text
        )
    }

    private fun parseTransactionDelete(text: String): VoiceCommand {
        // e.g. "Delete the 300 rupee auto transaction"
        val amount = VoiceNumberParser.parseAmountPaise(text)
        val descriptionKeywords = listOf("train", "ticket", "dinner", "lunch", "auto", "movie", "grocery", "food", "hotel", "rent")
        val desc = descriptionKeywords.firstOrNull { text.contains(it) }

        return VoiceCommand.DeleteTransaction(
            searchCriteria = TransactionSearchCriteria(
                description = desc,
                amountPaise = amount
            ),
            rawInput = text
        )
    }

    private fun parseQuery(
        text: String,
        intent: VoiceIntent,
        friends: List<Friend>,
        groups: List<FriendGroup>,
        categories: List<Category>
    ): VoiceCommand {
        val referencedFriends = VoiceEntityResolver.findReferencedFriends(text, friends)
        val targetFriend = referencedFriends.firstOrNull()
        val referencedGroup = VoiceEntityResolver.findReferencedGroup(text, groups)
        val category = if (intent == VoiceIntent.QUERY_CATEGORY_SPENDING) VoiceEntityResolver.resolveCategory(text, categories) else null
        val dateRange = VoiceDateParser.parseDateRangeFilter(text)

        val paymentMethod = when {
            text.contains("cash") -> PaymentMethod.CASH
            text.contains("upi") -> PaymentMethod.UPI
            text.contains("credit card") -> PaymentMethod.CREDIT_CARD
            text.contains("card") || text.contains("debit card") -> PaymentMethod.DEBIT_CARD
            text.contains("net banking") -> PaymentMethod.NET_BANKING
            else -> null
        }

        return VoiceCommand.Query(
            intent = intent,
            friendName = targetFriend?.name,
            friendId = targetFriend?.id,
            groupName = referencedGroup?.name,
            groupId = referencedGroup?.id,
            categoryName = category?.name,
            paymentMethod = paymentMethod,
            dateRange = dateRange,
            rawInput = text
        )
    }

    private fun extractTransactionTitle(text: String, defaultCategoryName: String): String {
        // Strip common command and currency words
        val clean = text.replace(Regex("""(?:\b|₹)\s*\d+(?:\.\d+)?\s*(?:k|thousand|hazar|lakh|crore)?(?:\s*(?:rupees|रुपये|રૂપિયા|rs))?\b"""), " ")
            .replace(Regex("""\b(?:add|create|record|spent|expense|jodo|likho|umero|for|ke liye|maate|માટે|के लिए|paid by|split with|divide|equally)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.length >= 3) {
            // Capitalize first letter
            return clean.replaceFirstChar { it.uppercase() }
        }
        return defaultCategoryName
    }
}
