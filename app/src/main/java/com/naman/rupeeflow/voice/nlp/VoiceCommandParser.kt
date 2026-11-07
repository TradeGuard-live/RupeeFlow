package com.naman.rupeeflow.voice.nlp

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.voice.model.*

/**
 * Parses natural language input into typed VoiceCommand objects.
 * Accurately extracts friends, groups, phone numbers, amounts, dates, and split configurations.
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

            // Friend management
            VoiceIntent.FRIEND_CREATE -> parseFriendCreate(normalized, text)
            VoiceIntent.FRIEND_UPDATE -> parseFriendUpdate(normalized, text, friends)
            VoiceIntent.FRIEND_DELETE -> parseFriendDelete(normalized, text, friends)
            VoiceIntent.FRIEND_SEARCH -> parseFriendSearch(normalized, text)
            VoiceIntent.FRIEND_ARCHIVE -> parseFriendDelete(normalized, text, friends)
            VoiceIntent.FRIEND_UNARCHIVE -> parseFriendSearch(normalized, text)

            // Group management
            VoiceIntent.GROUP_CREATE -> parseGroupCreate(normalized, text, friends)
            VoiceIntent.GROUP_UPDATE -> parseGroupUpdate(normalized, text, groups)
            VoiceIntent.GROUP_DELETE -> parseGroupDelete(normalized, text, groups)
            VoiceIntent.GROUP_ADD_MEMBER -> parseGroupAddMember(normalized, text, friends, groups)
            VoiceIntent.GROUP_REMOVE_MEMBER -> parseGroupRemoveMember(normalized, text, friends, groups)
            VoiceIntent.GROUP_LIST -> VoiceCommand.ListGroups(rawInput = text)

            // Navigation and Backup
            VoiceIntent.NAVIGATE_SCREEN -> parseNavigation(normalized, text)
            VoiceIntent.BACKUP_CREATE -> VoiceCommand.CreateBackup(rawInput = text)
            VoiceIntent.BACKUP_RESTORE -> VoiceCommand.RestoreBackup(rawInput = text)

            // Settlements
            VoiceIntent.CREATE_SETTLEMENT -> parseSettlementCreation(normalized, friends)
            VoiceIntent.EDIT_SETTLEMENT -> parseSettlementEdit(normalized, friends)
            VoiceIntent.REVERSE_SETTLEMENT -> parseSettlementReverse(normalized, friends)
            VoiceIntent.DELETE_SETTLEMENT -> parseSettlementDelete(normalized, friends)

            // Transactions
            VoiceIntent.EDIT_TRANSACTION -> parseTransactionEdit(normalized, friends, categories)
            VoiceIntent.DELETE_TRANSACTION -> parseTransactionDelete(normalized)
            VoiceIntent.SEARCH_TRANSACTION,

            // Queries
            VoiceIntent.QUERY_FRIEND_BALANCE,
            VoiceIntent.QUERY_ALL_BALANCES,
            VoiceIntent.QUERY_GROUP_BALANCE,
            VoiceIntent.QUERY_TOTAL_SPENDING,
            VoiceIntent.QUERY_MONTHLY_SPENDING,
            VoiceIntent.QUERY_CATEGORY_SPENDING,
            VoiceIntent.QUERY_PAYMENT_METHOD_SPENDING,
            VoiceIntent.QUERY_HIGHEST_EXPENSE,
            VoiceIntent.QUERY_TRANSACTION_DETAILS,
            VoiceIntent.QUERY_SETTLEMENT_HISTORY -> parseQuery(normalized, intent, friends, groups, categories)

            // Transaction creation
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
        }
    }

    private fun parseFriendCreate(normalized: String, rawText: String): VoiceCommand.CreateFriend {
        // Extract 10-digit phone number if present
        val phoneRegex = Regex("""\b[6-9]\d{9}\b""")
        val phoneMatch = phoneRegex.find(rawText) ?: phoneRegex.find(normalized)
        val phoneNumber = phoneMatch?.value

        // Strip phone number from text
        var nameText = if (phoneNumber != null) {
            normalized.replace(phoneNumber, " ")
        } else {
            normalized
        }

        // Strip phone related words
        val phoneKeywords = listOf(
            "with phone number", "with phone no", "with mobile number", "with mobile no", "with phone", "with number", "with mobile", "with",
            "phone number", "phone no", "mobile number", "mobile no", "number", "phone", "mobile",
            "sathe", "ke sath", "સાથે", "के साथ",
            "ફોન નંબર", "ફોન નો", "મોબાઈલ નંબર", "મોબાઇલ", "ફોન", "નંબર",
            "फोन नंबर", "मोबाइल नंबर", "फोन", "नंबर",
            "save karo", "save kar do", "save kar", "save", "સેવ કરો", "सेव करो", "rakho", "રાખો", "रखो"
        ).sortedByDescending { it.length }

        for (kw in phoneKeywords) {
            nameText = nameText.replace(kw, " ")
        }

        // Strip friend creation prefixes and trigger phrases (sorted by descending length)
        val triggerPhrases = listOf(
            "add a friend named", "add friend named", "create friend named", "create a friend named",
            "add new friend", "create new friend", "add friend", "create friend", "a new friend", "new friend",
            "naam ka naya dost jodo", "naam ka naya dost", "naam ka dost jodo", "naam ka dost",
            "ko naya dost jodo", "ko dost jodo", "naya dost jodo", "dost jodo",
            "dost ke roop me jodo", "dost ke roop mein jodo", "dost ke roop me", "dost ke roop mein",
            "naya dost banao", "dost banao", "naya dost",
            "namno navo mitra umero", "namno navo mitra", "namno mitra umero", "namno mitra",
            "navo mitra umero", "navo mitra", "mitra umero", "dost umero", "dost jodo", "mitra banavo",
            "tarike add karo", "tarike umero", "tarike jodo", "tarike",
            "નામનો નવો મિત્ર ઉમેરો", "નામનો મિત્ર ઉમેરો", "નવો મિત્ર ઉમેરો", "મિત્ર ઉમેરો",
            "દોસ્ત ઉમેરો", "દોસ્ત બનાવો", "દોસ્ત જોડો", "નવો મિત્ર", "નામનો મિત્ર",
            "નામનો", "નામની", "નામનું",
            "नाम का नया दोस्त जोड़ो", "नया दोस्त जोड़ो", "दोस्त जोड़ो", "दोस्त बनाओ", "नया दोस्त", "नाम का दोस्त",
            "नाम का", "नाम की", "नाम के",
            "named", "called", "naam ka", "naam ki", "naam ke", "naamno", "namno"
        ).sortedByDescending { it.length }

        for (phrase in triggerPhrases) {
            nameText = nameText.replace(phrase, " ")
        }

        // Clean suffixes and particles while preserving Indic matras and marks
        nameText = nameText.replace(Regex("""\b(?:ka|ki|ke|ko|ne|no|ni|nu|e|thi|se|મા|માં|મેં|કો|કા|કી|કે)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{M}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val genitiveSuffixes = listOf("નો", "ની", "નું", "કા", "કી", "કે", "का", "की", "के", "'s")
        val cleanedTokens = nameText.split(" ").map { token ->
            var t = token.trim()
            for (suf in genitiveSuffixes) {
                if (t.endsWith(suf) && t.length > suf.length) {
                    t = t.substring(0, t.length - suf.length)
                }
            }
            t
        }.filter { it.isNotBlank() && !genitiveSuffixes.contains(it) }
        nameText = cleanedTokens.joinToString(" ")

        // Capitalize friend name
        val friendName = if (nameText.isNotBlank()) {
            nameText.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        } else {
            ""
        }

        return VoiceCommand.CreateFriend(
            name = friendName,
            phoneNumber = phoneNumber,
            rawInput = rawText
        )
    }

    private fun parseFriendDelete(normalized: String, rawText: String, friends: List<Friend>): VoiceCommand.DeleteFriend {
        val referencedFriends = VoiceEntityResolver.findReferencedFriends(normalized, friends)
        val target = referencedFriends.firstOrNull()

        var name = target?.name
        if (name == null) {
            // Strip delete words and keywords
            var clean = normalized.replace(Regex("""\b(?:delete|remove|friend|dost|mitra|hatao|kaadho|nikalo|દોસ્ત|મિત્ર|હટાવો|કાઢો|કાઢી નાખો|हटाओ|डिलीट)\b"""), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            name = clean.ifBlank { "" }
        }

        return VoiceCommand.DeleteFriend(
            friendId = target?.id,
            friendName = name,
            rawInput = rawText
        )
    }

    private fun parseFriendUpdate(normalized: String, rawText: String, friends: List<Friend>): VoiceCommand.UpdateFriend {
        val referencedFriends = VoiceEntityResolver.findReferencedFriends(normalized, friends)
        val target = referencedFriends.firstOrNull()

        val phoneRegex = Regex("""\b[6-9]\d{9}\b""")
        val phoneMatch = phoneRegex.find(rawText) ?: phoneRegex.find(normalized)
        val newPhoneNumber = phoneMatch?.value

        return VoiceCommand.UpdateFriend(
            friendId = target?.id,
            friendName = target?.name,
            newPhoneNumber = newPhoneNumber,
            rawInput = rawText
        )
    }

    private fun parseFriendSearch(normalized: String, rawText: String): VoiceCommand.SearchFriend {
        var clean = normalized.replace(Regex("""\b(?:search|find|friend|dost|mitra|shodh|dhoondo|શોધો|દોસ્ત|મિત્ર|ढूंढो|खोजो)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return VoiceCommand.SearchFriend(query = clean, rawInput = rawText)
    }

    private fun parseGroupCreate(normalized: String, rawText: String, friends: List<Friend>): VoiceCommand.CreateGroup {
        // Find if initial members are referenced
        val referencedMembers = VoiceEntityResolver.findReferencedFriends(normalized, friends)
        val memberNames = referencedMembers.map { it.name }

        // Extract group name
        var groupName = normalized
        for (m in referencedMembers) {
            groupName = groupName.replace(m.name.lowercase(), " ")
        }

        val triggerPhrases = listOf(
            "create a group called", "create group called", "new group called",
            "create a group named", "create group named", "create a group", "create group", "new group",
            "group called", "ka group banao", "naam ka group banao", "group banao", "mandali banao",
            "namnu group banavo", "nu group banavo", "group banavo",
            "નામનું ગ્રૂપ બનાવો", "નામનું ગ્રુપ બનાવો", "ગ્રૂપ બનાવો", "ગ્રુપ બનાવો", "નું ગ્રૂપ", "નું ગ્રુપ",
            "કા ગ્રુપ બનાવો", "કા ગ્રૂપ બનાવો", "નામનું", "નું",
            "का ग्रुप बनाओ", "ग्रुप बनाओ", "का ग्रुप", "ग्रुप", "ગ્રૂપ", "ગ્રુપ", "group"
        ).sortedByDescending { it.length }

        for (phrase in triggerPhrases) {
            groupName = groupName.replace(phrase, " ")
        }

        groupName = groupName.replace(Regex("""\b(?:with|and|aur|sathe|ke sath|સાથે|કે સાથ|और|અને)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{M}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val formattedGroupName = if (groupName.isNotBlank()) {
            groupName.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        } else {
            ""
        }

        return VoiceCommand.CreateGroup(
            name = formattedGroupName,
            initialMemberNames = memberNames,
            rawInput = rawText
        )
    }

    private fun parseGroupAddMember(
        normalized: String,
        rawText: String,
        friends: List<Friend>,
        groups: List<FriendGroup>
    ): VoiceCommand.AddGroupMember {
        val targetGroup = VoiceEntityResolver.findReferencedGroup(normalized, groups)
        val referencedFriends = VoiceEntityResolver.findReferencedFriends(normalized, friends)

        return VoiceCommand.AddGroupMember(
            groupId = targetGroup?.id,
            groupName = targetGroup?.name,
            friendNames = referencedFriends.map { it.name },
            friendIds = referencedFriends.map { it.id },
            rawInput = rawText
        )
    }

    private fun parseGroupRemoveMember(
        normalized: String,
        rawText: String,
        friends: List<Friend>,
        groups: List<FriendGroup>
    ): VoiceCommand.RemoveGroupMember {
        val targetGroup = VoiceEntityResolver.findReferencedGroup(normalized, groups)
        val referencedFriends = VoiceEntityResolver.findReferencedFriends(normalized, friends)
        val targetFriend = referencedFriends.firstOrNull()

        return VoiceCommand.RemoveGroupMember(
            groupId = targetGroup?.id,
            groupName = targetGroup?.name,
            friendName = targetFriend?.name,
            friendId = targetFriend?.id,
            rawInput = rawText
        )
    }

    private fun parseGroupUpdate(
        normalized: String,
        rawText: String,
        groups: List<FriendGroup>
    ): VoiceCommand.UpdateGroup {
        val targetGroup = VoiceEntityResolver.findReferencedGroup(normalized, groups)
        var newName = normalized.replace(Regex("""\b(?:rename|change|group|name|to|into|badlo|naam|નામ બદલો|नाम बदलो)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return VoiceCommand.UpdateGroup(
            groupId = targetGroup?.id,
            groupName = targetGroup?.name,
            newName = newName.ifBlank { null },
            rawInput = rawText
        )
    }

    private fun parseGroupDelete(
        normalized: String,
        rawText: String,
        groups: List<FriendGroup>
    ): VoiceCommand.DeleteGroup {
        val targetGroup = VoiceEntityResolver.findReferencedGroup(normalized, groups)

        var groupName = targetGroup?.name
        if (groupName == null) {
            val clean = normalized.replace(Regex("""\b(?:delete|remove|group|hatao|kaadho|nikalo|ગ્રૂપ|ગ્રુપ|ડિલીટ|हटाओ|हटा दो|डिलीट)\b"""), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            groupName = clean.ifBlank { "" }
        }

        return VoiceCommand.DeleteGroup(
            groupId = targetGroup?.id,
            groupName = groupName,
            rawInput = rawText
        )
    }

    private fun parseNavigation(normalized: String, rawText: String): VoiceCommand.Navigate {
        val (screen, title) = when {
            normalized.contains("friend") || normalized.contains("dost") || normalized.contains("mitra") || normalized.contains("મિત્રો") || normalized.contains("દોસ્ત") ->
                "friends" to "Friends"
            normalized.contains("analytic") || normalized.contains("insight") || normalized.contains("report") || normalized.contains("chart") || normalized.contains("એનાલિટિક્સ") ->
                "analytics" to "Analytics"
            normalized.contains("transaction") || normalized.contains("history") || normalized.contains("list") || normalized.contains("લેવડદેવડ") || normalized.contains("ઇતિહાસ") ->
                "transactions" to "Transactions"
            normalized.contains("setting") || normalized.contains("preference") || normalized.contains("સેટિંગ્સ") || normalized.contains("सेटिंग्स") ->
                "settings" to "Settings"
            else ->
                "dashboard" to "Dashboard"
        }

        return VoiceCommand.Navigate(
            destination = screen,
            destinationTitle = title,
            rawInput = rawText
        )
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
        val date = VoiceDateParser.parseTransactionDate(text)
        val paymentMethod = VoiceEntityResolver.resolvePaymentMethod(text)
        val referencedFriends = VoiceEntityResolver.findReferencedFriends(text, friends)
        val targetFriend = referencedFriends.firstOrNull()

        // Direction: did I pay friend, or did friend pay me?
        val receivedKeywords = listOf("from", "received", "mila", "mile", "malya", "paasethi", "પાસેથી", "से मिला", "સે મળ્યા", "returned")
        val isReceived = receivedKeywords.any { text.contains(it) }

        val payerId = if (isReceived) targetFriend?.id else null
        val receiverId = if (isReceived) null else targetFriend?.id

        return VoiceCommand.CreateSettlement(
            amountPaise = amount,
            payerId = payerId,
            receiverId = receiverId,
            friendName = targetFriend?.name ?: "Friend",
            paymentMethod = paymentMethod,
            date = date,
            rawInput = text
        )
    }

    private fun parseSettlementEdit(text: String, friends: List<Friend>): VoiceCommand {
        val newAmount = VoiceNumberParser.parseAmountPaise(text)
        val targetFriend = VoiceEntityResolver.findReferencedFriends(text, friends).firstOrNull()
        val newPaymentMethod = if (text.contains("cash") || text.contains("upi") || text.contains("card") || text.contains("net banking")) {
            VoiceEntityResolver.resolvePaymentMethod(text)
        } else null

        return VoiceCommand.EditSettlement(
            friendName = targetFriend?.name,
            newAmountPaise = newAmount,
            newPaymentMethod = newPaymentMethod,
            rawInput = text
        )
    }

    private fun parseSettlementReverse(text: String, friends: List<Friend>): VoiceCommand {
        val targetFriend = VoiceEntityResolver.findReferencedFriends(text, friends).firstOrNull()
        return VoiceCommand.ReverseSettlement(
            friendName = targetFriend?.name,
            rawInput = text
        )
    }

    private fun parseSettlementDelete(text: String, friends: List<Friend>): VoiceCommand {
        val targetFriend = VoiceEntityResolver.findReferencedFriends(text, friends).firstOrNull()
        return VoiceCommand.DeleteSettlement(
            friendName = targetFriend?.name,
            rawInput = text
        )
    }

    private fun parseTransactionEdit(
        text: String,
        friends: List<Friend>,
        categories: List<Category>
    ): VoiceCommand {
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
        val clean = text.replace(Regex("""(?:\b|₹)\s*\d+(?:\.\d+)?\s*(?:k|thousand|hazar|lakh|crore)?(?:\s*(?:rupees|रुपये|રૂપિયા|rs))?\b"""), " ")
            .replace(Regex("""\b(?:add|create|record|spent|expense|jodo|likho|umero|for|ke liye|maate|માટે|के लिए|paid by|split with|divide|equally)\b"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.length >= 3) {
            return clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return defaultCategoryName
    }
}
