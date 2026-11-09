package com.naman.rupeeflow.voice.nlp

import com.naman.rupeeflow.voice.model.VoiceIntent

/**
 * Deterministic multilingual intent classifier across English, Hindi, Gujarati, and Hinglish.
 * Prioritizes friend, group, and system commands before financial transactions
 * to guarantee that non-transaction operations like "Add friend" are never misclassified as expenses.
 */
object VoiceIntentClassifier {

    fun classify(rawText: String): VoiceIntent {
        val normalized = VoiceTextNormalizer.normalize(rawText)
        if (normalized.isBlank()) return VoiceIntent.UNKNOWN_INTENT

        // 1. Conversational controls (Highest priority)
        if (isConfirm(normalized)) return VoiceIntent.CONFIRM
        if (isCancel(normalized)) return VoiceIntent.CANCEL
        if (isHelp(normalized)) return VoiceIntent.HELP

        // 2. System Navigation & Backup
        if (isNavigation(normalized)) return VoiceIntent.NAVIGATE_SCREEN
        if (isBackupCreate(normalized)) return VoiceIntent.BACKUP_CREATE
        if (isBackupRestore(normalized)) return VoiceIntent.BACKUP_RESTORE

        // 3. Group Management (Check before Friends because a group command can mention members)
        val groupIntent = classifyGroupIntent(normalized)
        if (groupIntent != null) return groupIntent

        // 4. Friend Management (STRICT: Prevents "Add friend" from ever falling through to CREATE_EXPENSE)
        val friendIntent = classifyFriendIntent(normalized)
        if (friendIntent != null) return friendIntent

        // 5. Settlement mutations (Edit, Reverse, Delete)
        if (containsAny(normalized, "reverse settlement", "undo settlement", "settlement cancel", "paachhu lo", "પાછું લો", "रिवर्स")) {
            return VoiceIntent.REVERSE_SETTLEMENT
        }
        if (containsAny(normalized, "edit settlement", "change settlement", "settlement badlo", "સેટલમેન્ટ બદલો", "સેટલમેન્ટ એડિટ")) {
            return VoiceIntent.EDIT_SETTLEMENT
        }
        if (containsAny(normalized, "delete settlement", "remove settlement", "settlement hatao", "સેટલમેન્ટ ડીલીટ", "सेटेलमेंट हटाओ")) {
            return VoiceIntent.DELETE_SETTLEMENT
        }

        // 6. Transaction mutations (Edit, Delete)
        if (isDeleteTransaction(normalized)) return VoiceIntent.DELETE_TRANSACTION
        if (isEditTransaction(normalized)) return VoiceIntent.EDIT_TRANSACTION

        // 7. Financial Queries (Spending, Balances, Highest expense)
        val queryIntent = classifyQuery(normalized)
        if (queryIntent != null) return queryIntent

        // 8. Settlement Creation (e.g. "Pay Jay 500", "Jay ko 500 diye", "Settle 300 with Manav", "Jay પાસેથી 300 મળ્યા")
        if (isCreateSettlement(normalized)) return VoiceIntent.CREATE_SETTLEMENT

        // 9. Transaction Creation (Refund, Income, Transfer)
        if (containsAny(normalized, "refund", "wapas mile", "paise wapas", "parat malya", "પરત મળ્યા", "रिफंड")) {
            return VoiceIntent.CREATE_REFUND
        }
        if (containsAny(normalized, "salary", "income", "bonus", "kamai", "aavak", "aavya", "આવક", "कमाई", "सैलरी", "પગાર")) {
            return VoiceIntent.CREATE_INCOME
        }
        if (containsAny(normalized, "transfer", "transferred", "bheje", "moklya", "મોકલ્યા", "ट्रांसफर")) {
            return VoiceIntent.CREATE_TRANSFER
        }

        // 10. Default creation is Expense when amounts or spending keywords appear
        if (isCreateExpense(normalized)) return VoiceIntent.CREATE_EXPENSE

        return VoiceIntent.UNKNOWN_INTENT
    }

    private fun isConfirm(text: String): Boolean {
        if (text.split(" ").size > 3 || Regex("""\d""").containsMatchIn(text)) return false
        return matchesAnyWord(
            text,
            "confirm", "yes", "save", "save it", "done", "okay", "ok", "haan", "ha", "theek hai",
            "sahi hai", "barabar", "saachvo", "સાચવો", "હા", "બરાબર", "हाँ", "सेव करो", "ठीक है", "सही है"
        )
    }

    private fun isCancel(text: String): Boolean {
        if (text.split(" ").size > 3) return false
        return matchesAnyWord(
            text,
            "cancel", "stop", "abort", "no", "nahin", "nahi", "nathi", "reva do", "mat karo",
            "રદ કરો", "નહીં", "રહેવા દો", "रद्द करो", "नहीं", "मत करो", "बंद करो"
        )
    }

    private fun isHelp(text: String): Boolean {
        return containsAny(
            text,
            "help", "kya kar sakte ho", "madad", "features", "options",
            "શું કરી શકો", "મદદ", "क्या कर सकते हो", "सहायता"
        )
    }

    private fun isNavigation(text: String): Boolean {
        val hasNavVerb = containsAny(text, "open", "go to", "show screen", "kholo", "खोलो", "ખોલો", "બતાવો")
        val hasScreen = containsAny(text, "friends", "analytics", "transactions", "history", "dashboard", "home", "settings", "મિત્રો", "સેટિંગ્સ", "એનાલિટિક્સ")
        val isExplicitNav = containsAny(text, "open friends", "open analytics", "open settings", "open transactions", "go to friends", "go to settings", "go to analytics")
        return isExplicitNav || (hasNavVerb && hasScreen && !text.contains("group") && VoiceNumberParser.parseAmountPaise(text) == null)
    }

    private fun isBackupCreate(text: String): Boolean {
        return containsAny(text, "create backup", "take backup", "make backup", "backup lo", "બેકઅપ લો", "બેકઅપ બનાવો", "बैकअप लो", "बैकअप बनाओ")
    }

    private fun isBackupRestore(text: String): Boolean {
        return containsAny(text, "restore backup", "restore data", "backup restore", "બેકઅપ રીસ્ટોર", "રીસ્ટોર કરો", "बैकअप रिस्टोर", "रिस्टोर करो")
    }

    /**
     * Friend Management classification.
     * Prevents ANY friend creation command from ever being classified as an expense.
     */
    private fun classifyFriendIntent(text: String): VoiceIntent? {
        val hasFriendWord = containsAny(text, "friend", "dost", "mitra", "મિત્ર", "દોસ્ત", "દોસ્તો", "दोस्त", "દોસ્ત", "ફ્રેન્ડ", "ફ્રેન્ડ્સ", "फ्रेंड", "फ्रेंड्स")

        // 1. Friend Delete
        if (hasFriendWord && containsAny(text, "delete", "remove", "hatao", "kaadho", "kaadhi", "કાઢી નાખો", "કાઢી", "હટાવો", "हटाओ", "हटा दो", "डिलीट")) {
            return VoiceIntent.FRIEND_DELETE
        }

        // 2. Friend Search
        if (hasFriendWord && containsAny(text, "search", "find", "shodh", "dhoondo", "શોધો", "ढूंढो")) {
            return VoiceIntent.FRIEND_SEARCH
        }

        // 3. Friend Update / Edit (Phone number change, name change)
        val hasEditKeyword = containsAny(text, "change", "update", "rename", "edit", "badlo", "badli", "બદલો", "બદલીને", "बदलो", "बदलकर")
        val hasFieldKeyword = containsAny(text, "name", "naam", "phone", "number", "mobile", "નામ", "નંબર", "ફોન", "नाम", "नंबर", "फोन")
        val isExplicitFriendUpdate = (hasFriendWord && hasEditKeyword) ||
                (hasEditKeyword && hasFieldKeyword && !containsAny(text, "transaction", "expense", "settlement", "kharch", "hisaab", "ખર્ચ", "ચુકવણી", "खर्च")) ||
                (text.contains("rename", ignoreCase = true) && !containsAny(text, "transaction", "expense", "settlement", "group", "category"))
        if (isExplicitFriendUpdate) {
            return VoiceIntent.FRIEND_UPDATE
        }

        // 4. Friend Create
        // Explicit patterns that always indicate friend creation
        val isExplicitFriendCreate = containsAny(
            text,
            "add friend", "new friend", "create friend", "add a friend", "a new friend",
            "dost jodo", "mitra umero", "dost umero", "dost banao", "mitra banavo",
            "naya dost", "navo mitra", "tarike add karo", "tarike umero",
            "મિત્ર ઉમેરો", "દોસ્ત ઉમેરો", "દોસ્ત બનાવો", "દોસ્ત જોડો", "નવો મિત્ર", "મિત્ર બનાવો",
            "दोस्त जोड़ो", "दोस्त बनाओ", "नया दोस्त", "दोस्त के रूप में जोड़ो"
        )

        // Patterns like "Jay naam ka naya dost jodo", "Jay naamno mitra umero", "Pratik no phone number 9876543210 save karo"
        val hasPhoneSavePattern = (hasFriendWord || containsAny(text, "phone", "number", "ફોન", "નંબર", "नंबर")) &&
                containsAny(text, "save karo", "save kar do", "સેવ કરો", "सेव करो") &&
                Regex("""\b[6-9]\d{9}\b""").containsMatchIn(text)

        if (isExplicitFriendCreate || hasPhoneSavePattern) {
            return VoiceIntent.FRIEND_CREATE
        }

        // If command starts with "friend" or "add friend" or starts with Indic friend verbs
        if (text.startsWith("friend ") || text.startsWith("add friend") || text.startsWith("create friend")) {
            return VoiceIntent.FRIEND_CREATE
        }

        return null
    }

    /**
     * Group Management classification.
     */
    private fun classifyGroupIntent(text: String): VoiceIntent? {
        val hasGroupWord = containsAny(text, "group", "grup", "mandali", "ગ્રૂપ", "ગ્રુપ", "ग्रुप", "मंडल")
        val hasAmount = VoiceNumberParser.parseAmountPaise(text) != null

        // 1. Group List
        if (hasGroupWord && (containsAny(text, "show groups", "list groups", "all groups", "badha group", "બધા ગ્રૂપ", "બધા ગ્રુપ", "સારે ગ્રુપ", "सारे ग्रुप", "ग्रुप दिखाओ", "ગ્રૂપ બતાવો") ||
            ((containsWord(text, "show") || containsWord(text, "list") || containsWord(text, "all") || containsWord(text, "badha") || containsWord(text, "બધા")) &&
             !containsAny(text, "create", "banao", "banavo", "called", "બનાવો", "बनाओ")))) {
            return VoiceIntent.GROUP_LIST
        }

        // 2. Group Delete
        if (hasGroupWord && containsAny(text, "delete", "remove", "hatao", "kaadho", "કાઢી નાખો", "હટાવો", "हटाओ", "डिलीट")) {
            return VoiceIntent.GROUP_DELETE
        }

        // 3. Remove Member from Group (e.g. "Remove Jay from College Trip", "જયને હોસ્ટેલ ગ્રુપમાંથી કાઢો")
        if (containsAny(text, "remove", "kaadho", "nikalo", "hatao", "કાઢો", "હટાવો", "हटाओ", "निकालो") &&
            containsAny(text, "from", "mathi", "માંથી", "સે", "se")
        ) {
            return VoiceIntent.GROUP_REMOVE_MEMBER
        }

        // 4. Add Member to Group (e.g. "Add Jay to College Trip", "જય અને મનનને હોસ્ટેલ ગ્રુપમાં ઉમેરો")
        if (containsAny(text, "add", "umero", "jodo", "ઉમેરો", "જોડો", "जोड़ो") &&
            containsAny(text, "to", "ma", "mein", "માં", "મેં", "में") &&
            !hasAmount && !text.contains("friend") && !text.contains("મિત્ર") && !text.contains("દોસ્ત")
        ) {
            return VoiceIntent.GROUP_ADD_MEMBER
        }

        // 5. Rename / Update Group
        if (hasGroupWord && containsAny(text, "rename", "change name", "naam badlo", "નામ બદલો", "नाम बदलो")) {
            return VoiceIntent.GROUP_UPDATE
        }

        // 6. Group Create
        val hasCreateVerb = containsAny(text, "create", "new", "banao", "banavo", "ઉમેરો", "બનાવો", "જોડો", "बनाओ", "जोड़ो", "નવું", "नया")
        if (hasGroupWord && (hasCreateVerb || containsAny(text, "called", "ka group", "nu group", "નું ગ્રૂપ", "નું ગ્રુપ", "का ग्रुप"))) {
            return VoiceIntent.GROUP_CREATE
        }

        return null
    }

    private fun isDeleteTransaction(text: String): Boolean {
        val hasDeleteKeyword = containsAny(
            text,
            "delete", "remove", "hata do", "hatao", "kaadh", "kaadhi naakho", "nikal do",
            "કાઢી નાખો", "હટાવો", "हटाओ", "हटा दो", "डिलीट", "मिटाओ"
        )
        return hasDeleteKeyword && !text.contains("settlement") && !text.contains("friend") && !text.contains("group") && !text.contains("મિત્ર") && !text.contains("દોસ્ત") && !text.contains("ગ્રૂપ")
    }

    private fun isEditTransaction(text: String): Boolean {
        val hasEditKeyword = containsAny(
            text,
            "change", "edit", "update", "modify", "badlo", "badal do", "ferfar", "sudharo",
            "बदलो", "बदल दो", "સુધારો", "ફેરફાર કરો", "બદલો", "बदलना"
        )
        return hasEditKeyword && !text.contains("settlement") && !text.contains("friend") && !text.contains("group") && !text.contains("મિત્ર") && !text.contains("દોસ્ત") && !text.contains("ગ્રૂપ")
    }

    private fun isCreateSettlement(text: String): Boolean {
        // e.g. "Pay Jay 500", "Jay ko 500 diye", "Settle 500 with Manav", "Jay returned 200", "Jay પાસેથી 300 મળ્યા"
        val hasSettlementVerb = containsAny(
            text,
            "settle", "settled", "paid to", "pay to", "pay ", "returned", "de diye", "diye", "chukavya",
            "chukavvu", "aapya", "malya", "chukaya", "lautaye", "laotaye", "wapas kiye",
            "ચૂકવ્યા", "આપ્યા", "મળ્યા", "સેટલ", "चुकाए", "दे दिए", "लौटाए", "वापस दिए",
            "hisab", "hisaab", "chukte", "chukta", "barabar", "હિસાબ", "ચૂકતે", "हिसाब", "चुकता"
        )
        val hasTransactionFlag = containsAny(
            text,
            "ticket", "dinner", "lunch", "movie", "auto", "food", "grocery", "bill", "chai", "coffee",
            "bhojan", "khana", "ભોજન", "ચા", "कॉफी", "भोजन", "खाना", "ke liye", "mate", "for", "માટે", "કે लिए"
        )
        return hasSettlementVerb && (!hasTransactionFlag || text.contains("settle") || text.contains("hisab") || text.contains("હિસાબ") || text.contains("हिसाब"))
    }

    private fun isCreateExpense(text: String): Boolean {
        // If text has an amount or typical expense creation words
        val hasVerb = containsAny(
            text,
            "add", "create", "record", "spent", "expense", "kharch", "kharcha", "jodo", "likho",
            "likh lo", "umero", "nondo", "paid for", "bought", "khareed", "kharidya",
            "ઉમેરો", "ખર્ચ", "જોડો", "લખો", "ખરીદ્યા", "जोड़ो", "लिखो", "खर्च", "खरीदा", "व्यय"
        )
        val hasAmount = VoiceNumberParser.parseAmountPaise(text) != null
        val hasSplitKeyword = containsAny(text, "split", "divide", "baant", "vahecho", "શેર", "ભાગ")
        return hasVerb || (hasAmount && hasSplitKeyword) || hasAmount
    }

    private fun classifyQuery(text: String): VoiceIntent? {
        val hasQuestionWord = containsAny(
            text,
            "how much", "how many", "show", "what", "which", "who", "kitna", "kitne", "kon", "kiska",
            "ketla", "ketlo", "kone", "કોણે", "કેટલા", "કેટલો", "કયા", "कितना", "कितने", "किसका", "किसने", "दिखाओ", "બતાવો"
        )

        // 1. Highest Expense Query (e.g. "Show my highest expense", "sabse bada kharch", "sauthi moto kharch")
        if (containsAny(
                text,
                "highest expense", "biggest expense", "largest expense", "highest spending",
                "sabse bada kharch", "sabse bada kharcha", "sauthi moto kharch",
                "સૌથી મોટો ખર્ચ", "સૌથી મોટો", "सबसे बड़ा खर्च", "सबसे बड़ा"
            )
        ) {
            return VoiceIntent.QUERY_HIGHEST_EXPENSE
        }

        // 2. Friend / Friend Balances
        if (containsAny(text, "owe me", "i owe", "lene hain", "dene hain", "levana", "aapvana", "લેવાના", "આપવાના", "लेने हैं", "देने हैं", "उधार")) {
            return VoiceIntent.QUERY_FRIEND_BALANCE
        }

        if (containsAny(text, "all balances", "unsettled", "sabka hisab", "badha hisab", "am i settled", "બધા હિસાબ", "सबકા हिसाब", "बाकी है")) {
            return VoiceIntent.QUERY_ALL_BALANCES
        }

        if (containsAny(text, "group spend", "group balance", "group expenses", "hostel friends spend", "ગ્રૂપ ખર્ચ", "ગ્રુપ ખર્ચ", "ग्रुप खर्च")) {
            return VoiceIntent.QUERY_GROUP_BALANCE
        }

        val hasAmount = VoiceNumberParser.parseAmountPaise(text) != null

        // 3. Spending by category / payment method / period
        if (hasQuestionWord && containsAny(text, "spent on", "food spending", "travel spending", "grocery spending", "ખર્ચ કર્યો", "ખર્ચ થયો", "खर्च किया", "खर्च हुआ") && hasCategoryKeyword(text)) {
            return VoiceIntent.QUERY_CATEGORY_SPENDING
        }

        if (hasQuestionWord && containsAny(text, "cash", "upi", "card", "net banking") && containsAny(text, "spent", "spend", "kharcha", "transactions", "ખર્ચ")) {
            return VoiceIntent.QUERY_PAYMENT_METHOD_SPENDING
        }

        if ((hasQuestionWord || !hasAmount) && containsAny(text, "this month", "is mahine", "aa mahine", "monthly", "આ મહિને", "इस महीने")) {
            return VoiceIntent.QUERY_MONTHLY_SPENDING
        }

        if ((hasQuestionWord || !hasAmount) && containsAny(text, "total spent", "total expense", "total spending", "kitna kharch", "ketlo kharch", "કુલ ખર્ચ", "कुल खर्च")) {
            return VoiceIntent.QUERY_TOTAL_SPENDING
        }

        // 4. Transaction / Settlement History queries
        if (containsAny(text, "settlements with", "settlement history", "purane settlement", "જૂના સેટલમેન્ટ", "पुराने सेटलमेंट")) {
            return VoiceIntent.QUERY_SETTLEMENT_HISTORY
        }

        if (containsAny(text, "last", "recent", "transactions yesterday", "pichhle", "chhella", "છેલ્લા", "पिछले", "हाल के") &&
            containsAny(text, "transaction", "expenses", "spending", "kharch", "લેવડદેવડ")
        ) {
            return VoiceIntent.QUERY_TRANSACTION_DETAILS
        }

        if (hasQuestionWord && containsAny(text, "spent", "balance", "hisab", "kharch", "owe")) {
            return VoiceIntent.QUERY_TOTAL_SPENDING
        }

        return null
    }

    private fun hasCategoryKeyword(text: String): Boolean {
        return containsAny(
            text,
            "food", "dining", "dinner", "lunch", "grocery", "groceries", "travel", "transport",
            "train", "auto", "cab", "rent", "shopping", "movie", "entertainment", "bills",
            "ખાવાનું", "જમવાનું", "શાકભાજી", "પ્રવાસ", "ભાડું", "खाना", "किराया", "सब्जी", "यात्रा"
        )
    }

    private fun matchesAnyWord(text: String, vararg targets: String): Boolean {
        val trimmed = text.trim()
        for (target in targets) {
            if (trimmed == target || trimmed.startsWith("$target ") || trimmed.endsWith(" $target") || trimmed.contains(" $target ")) {
                return true
            }
        }
        return false
    }

    private fun containsAny(text: String, vararg targets: String): Boolean {
        for (target in targets) {
            if (text.contains(target)) return true
        }
        return false
    }

    private fun containsWord(text: String, word: String): Boolean {
        val pattern = Regex("""(?:\b|^|\s)${Regex.escape(word)}(?:\b|$|\s)""")
        return pattern.containsMatchIn(text)
    }
}
