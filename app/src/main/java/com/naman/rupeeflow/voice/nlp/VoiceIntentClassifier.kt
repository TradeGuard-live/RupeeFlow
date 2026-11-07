package com.naman.rupeeflow.voice.nlp

import com.naman.rupeeflow.voice.model.VoiceIntent

/**
 * Deterministic multilingual intent classifier across English, Hindi, Gujarati, and Hinglish.
 */
object VoiceIntentClassifier {

    fun classify(rawText: String): VoiceIntent {
        val normalized = VoiceTextNormalizer.normalize(rawText)
        if (normalized.isBlank()) return VoiceIntent.UNKNOWN_INTENT

        // 1. Exact / High-priority conversational controls
        if (isConfirm(normalized)) return VoiceIntent.CONFIRM
        if (isCancel(normalized)) return VoiceIntent.CANCEL
        if (isHelp(normalized)) return VoiceIntent.HELP

        // 2. Settlement mutations (Edit, Reverse, Delete)
        if (containsAny(normalized, "reverse settlement", "undo settlement", "settlement cancel", "paachhu lo", "પાછું લો", "रिवर्स")) {
            return VoiceIntent.REVERSE_SETTLEMENT
        }
        if (containsAny(normalized, "edit settlement", "change settlement", "settlement badlo", "સેટલમેન્ટ બદલો", "સેટલમેન્ટ એડિટ")) {
            return VoiceIntent.EDIT_SETTLEMENT
        }
        if (containsAny(normalized, "delete settlement", "remove settlement", "settlement hatao", "સેટલમેન્ટ ડીલીટ", "सेटेलमेंट हटाओ")) {
            return VoiceIntent.DELETE_SETTLEMENT
        }

        // 3. Transaction mutations (Edit, Delete)
        if (isDeleteTransaction(normalized)) return VoiceIntent.DELETE_TRANSACTION
        if (isEditTransaction(normalized)) return VoiceIntent.EDIT_TRANSACTION

        // 4. Financial Queries
        val queryIntent = classifyQuery(normalized)
        if (queryIntent != null) return queryIntent

        // 5. Settlement Creation (e.g. "Pay Jay 500", "Jay ko 500 diye", "Settle 300 with Manav", "Jay પાસેથી 300 મળ્યા")
        if (isCreateSettlement(normalized)) return VoiceIntent.CREATE_SETTLEMENT

        // 6. Transaction Creation (Refund, Income, Transfer, Expense)
        if (containsAny(normalized, "refund", "wapas mile", "paise wapas", "parat malya", "પરત મળ્યા", "रिफंड")) {
            return VoiceIntent.CREATE_REFUND
        }
        if (containsAny(normalized, "salary", "income", "bonus", "kamai", "aavak", "aavya", "આવક", "कमाई", "सैलरी")) {
            return VoiceIntent.CREATE_INCOME
        }
        if (containsAny(normalized, "transfer", "transferred", "bheje", "moklya", "મોકલ્યા", "ट्रांसफर")) {
            return VoiceIntent.CREATE_TRANSFER
        }

        // Default creation is Expense when amounts or spending keywords appear
        if (isCreateExpense(normalized)) return VoiceIntent.CREATE_EXPENSE

        return VoiceIntent.UNKNOWN_INTENT
    }

    private fun isConfirm(text: String): Boolean {
        return matchesAnyWord(
            text,
            "confirm", "yes", "save", "save it", "done", "okay", "ok", "haan", "ha", "theek hai",
            "sahi hai", "barabar", "saachvo", "સાચવો", "હા", "બરાબર", "हाँ", "सेव करो", "ठीक है", "सही है"
        )
    }

    private fun isCancel(text: String): Boolean {
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

    private fun isDeleteTransaction(text: String): Boolean {
        val hasDeleteKeyword = containsAny(
            text,
            "delete", "remove", "hata do", "hatao", "kaadh", "kaadhi naakho", "nikal do",
            "કાઢી નાખો", "હટાવો", "हटाओ", "हटा दो", "डिलीट", "मिटाओ"
        )
        return hasDeleteKeyword && !text.contains("settlement")
    }

    private fun isEditTransaction(text: String): Boolean {
        val hasEditKeyword = containsAny(
            text,
            "change", "edit", "update", "modify", "badlo", "badal do", "ferfar", "sudharo",
            "बदलो", "बदल दो", "સુધારો", "ફેરફાર કરો", "બદલો", "बदलना"
        )
        return hasEditKeyword && !text.contains("settlement")
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
            "bhojan", "khana", "ભોજન", "ચા", "कॉफी", "भोजन", "खाना", "ke liye", "mate", "for", "માટે", "के लिए"
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

        // 1. Friend / Friend Balances
        if (containsAny(text, "owe me", "i owe", "lene hain", "dene hain", "levana", "aapvana", "લેવાના", "આપવાના", "लेने हैं", "देने हैं", "उधार")) {
            return VoiceIntent.QUERY_FRIEND_BALANCE
        }

        if (containsAny(text, "all balances", "unsettled", "sabka hisab", "badha hisab", "am i settled", "બધા હિસાબ", "सबका हिसाब", "बाकी है")) {
            return VoiceIntent.QUERY_ALL_BALANCES
        }

        if (containsAny(text, "group spend", "group balance", "group expenses", "hostel friends spend", "ગ્રૂપ ખર્ચ", "ग्रुप खर्च")) {
            return VoiceIntent.QUERY_GROUP_BALANCE
        }

        val hasAmount = VoiceNumberParser.parseAmountPaise(text) != null

        // 2. Spending by category / payment method / period
        if (hasQuestionWord && containsAny(text, "spent on", "food spending", "travel spending", "grocery spending", "ખર્ચ કર્યો", "खर्च किया") && hasCategoryKeyword(text)) {
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

        // 3. Transaction / Settlement History queries
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
}
