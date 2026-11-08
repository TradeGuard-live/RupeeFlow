package com.naman.rupeeflow.voice.nlp

import com.naman.rupeeflow.domain.model.Friend
import java.util.Locale

data class FriendExtractionResult(
    val name: String,
    val phoneNumber: String? = null,
    val isAmbiguousOrMissing: Boolean = false
)

data class ExpenseTitleExtractionResult(
    val title: String,
    val isMissing: Boolean = false
)

/**
 * High-precision multilingual entity extraction engine for RupeeFlow Voice Assistant.
 * Strictly separates command grammar/markers from true domain entities
 * across English, Hindi, Gujarati, Hinglish, and mixed speech.
 */
object VoiceEntityExtractor {

    private val PHONE_REGEX = Regex("""\b[6-9]\d{9}\b""")

    private val GENITIVE_SUFFIXES = listOf(
        "નો", "ની", "નું", "કા", "કી", "કે", "ને", "ने", "કો", "को", "का", "की", "के", "'s"
    )

    private val PARTICLES = listOf(
        "ka", "ki", "ke", "ko", "ne", "no", "ni", "nu", "e", "thi", "se",
        "મા", "માં", "મેં", "કો", "કા", "કી", "કે", "પર", "pe", "par",
        "maate", "mate", "સાથે", "sathe", "ke sath", "ke liye", "ma", "maa", "mein", "me"
    )

    private val FRIEND_COMMAND_TOKENS = setOf(
        "add", "create", "new", "friend", "friends", "named", "called",
        "please", "a", "an", "the", "my", "to", "as", "in", "list",
        "dost", "dosto", "doston", "mitra", "mitro", "jodo", "umero", "banao", "banavo",
        "naya", "navo", "tarike", "roop", "rakho", "save", "karo", "do",
        "દોસ્ત", "દોસ્તો", "મિત્ર", "મિત્રો", "ઉમેરો", "બનાવો", "જોડો", "નવો", "નવી", "નામનો", "નામની", "નામનું",
        "તરીકે", "સાચવો", "રાખો", "સેવ", "કરો",
        "दोस्त", "दोस्तों", "मित्र", "मित्रों", "जोड़ो", "बनाओ", "नया", "नयी", "नई", "नाम", "का", "की", "કે", "के",
        "रूप", "में", "सेव", "करो", "कर", "दो", "रखो",
        "ફ્રેન્ડ", "ફ્રેન્ડ્સ", "फ्रेंड", "फ्रेंड्स"
    )

    private val COMMAND_OR_NOISE_TOKENS = setOf(
        "add", "expense", "expenses", "transaction", "transactions", "record", "create",
        "spent", "paid", "rupees", "rupaye", "rupya", "inr", "rs", "cash", "upi", "card",
        "today", "yesterday", "tomorrow", "for", "on", "in", "at", "of", "please",
        "kharcha", "kharch", "diye", "diya", "kiya", "kare", "karo", "do",
        "खर्च", "खर्चा", "दिए", "दिया", "किया", "करो", "रुपये", "रुपया",
        "kharchya", "karyo", "karya", "aapya", "aapo", "chukvya",
        "ખર્ચ", "ખર્ચ્યા", "કર્યો", "કર્યા", "આપ્યા", "ચૂકવ્યા", "રૂપિયા", "રુપિયા"
    )

    /**
     * Determines whether a candidate name is merely a command phrase or fragment.
     */
    fun isInvalidFriendName(name: String): Boolean {
        if (name.isBlank() || name.length < 2) return true
        val lower = name.lowercase(Locale.ROOT)
        val forbiddenSubstrings = listOf(
            "add a friend", "add friend", "create friend", "new friend", "my friend",
            "dost jodo", "mitra umero", "dost banao", "mitra banavo",
            "દોસ્ત ઉમેરો", "મિત્ર ઉમેરો", "દોસ્ત બનાવો", "મિત્ર બનાવો", "દોસ્ત જોડો",
            "दोस्त जोड़ो", "दोस्त बनाओ", "मित्र जोड़ो", "नया दोस्त"
        )
        for (sub in forbiddenSubstrings) {
            if (lower.contains(sub)) return true
        }
        val words = lower.split(" ").filter { it.isNotBlank() }
        if (words.all { FRIEND_COMMAND_TOKENS.contains(it) }) return true
        return false
    }

    /**
     * Extracts friend name and optional 10-digit Indian mobile number from voice input.
     * Returns isAmbiguousOrMissing = true if no genuine person name is provided.
     */
    fun extractFriendName(normalizedText: String, rawText: String): FriendExtractionResult {
        // 1. Extract 10-digit mobile number if present
        val phoneMatch = PHONE_REGEX.find(rawText) ?: PHONE_REGEX.find(normalizedText)
        val phoneNumber = phoneMatch?.value

        // Strip phone number from normalized text
        var text = if (phoneNumber != null) {
            normalizedText.replace(phoneNumber, " ")
        } else {
            normalizedText
        }

        // Strip phone related phrases
        val phonePhrases = listOf(
            "with phone number", "with phone no", "with mobile number", "with mobile no",
            "with phone", "with number", "with mobile", "with",
            "phone number", "phone no", "mobile number", "mobile no", "number", "phone", "mobile",
            "sathe", "ke sath", "સાથે", "કે સાથ",
            "ફોન નંબર", "ફોન નો", "મોબાઈલ નંબર", "મોબાઇલ", "ફોન", "નંબર",
            "फोन नंबर", "मोबाइल नंबर", "फोन", "नंबर",
            "save karo", "save kar do", "save kar", "save", "સેવ કરો", "सेવ", "सेव करो", "rakho", "રાખો", "रखो"
        ).sortedByDescending { it.length }

        for (phrase in phonePhrases) {
            text = text.replace(phrase, " ")
        }

        text = text.replace(Regex("\\s+"), " ").trim()

        // 2. High-precision structural pattern matching
        var candidateName: String? = null

        // Pattern A: "Add a friend named <NAME>", "Add friend called <NAME>", "Add a friend <NAME>", "Add friend <NAME>"
        val directPrefixRegex = Regex(
            """^(?:please\s+)?(?:add\s+(?:a\s+)?(?:new\s+)?friend(?:\s+called|\s+named)?|create\s+(?:a\s+)?(?:new\s+)?friend(?:\s+called|\s+named)?|add\s+new\s+friend|create\s+friend|add\s+a\s+friend|add\s+friend)\s+(.+)$""",
            RegexOption.IGNORE_CASE
        )
        val directPrefixMatch = directPrefixRegex.find(text)
        if (directPrefixMatch != null) {
            candidateName = directPrefixMatch.groupValues[1]
        }

        // Pattern B: "Add <NAME> to my friends", "Add <NAME> to friend list", "Add <NAME> as a friend"
        if (candidateName == null) {
            val infixRegex = Regex(
                """^(?:please\s+)?(?:add|create)\s+(.+?)\s+(?:to\s+(?:my\s+)?friends|to\s+friend\s+list|to\s+friends|as\s+(?:a\s+)?friend)$""",
                RegexOption.IGNORE_CASE
            )
            val infixMatch = infixRegex.find(text)
            if (infixMatch != null) {
                candidateName = infixMatch.groupValues[1]
            }
        }

        // Pattern C: Hindi / Hinglish structures
        // e.g. "Jay ko friend list mein add karo", "Sukrut naam ka dost jodo", "ek dost Sukrut jodo"
        if (candidateName == null) {
            val hindiPatterns = listOf(
                Regex("""^(.+?)\s+(?:ko\s+)?(?:friend\s+list\s+mein\s+add\s+karo|friend\s+list\s+me\s+add\s+karo|dost\s+ke\s+roop\s+mein\s+jodo|dost\s+ke\s+roop\s+me\s+jodo|dost\s+jodo|dost\s+banao|friend\s+banao)$""", RegexOption.IGNORE_CASE),
                Regex("""^(.+?)\s+naam\s+ka\s+(?:naya\s+)?dost(?:\s+jodo|\s+banao)?$""", RegexOption.IGNORE_CASE),
                Regex("""^(?:ek\s+)?(?:naya\s+)?dost\s+(.+?)\s+(?:jodo|banao)$""", RegexOption.IGNORE_CASE),
                Regex("""^(.+?)\s*(?:को|नाम\s*का)?\s*(?:दोस्त|मित्र)\s*(?:के\s*रूप\s*में)?\s*(?:जोड़ो|बनाओ)$"""),
                Regex("""^(?:एक\s+)?(?:दोस्त|मित्र)\s+(.+?)\s*(?:जोड़ो|बनाओ)$"""),
                Regex("""^(.+?)\s*नाम\s*का\s*(?:दोस्त|मित्र)\s*(?:जोड़ो|बनाओ)$""")
            )
            for (pattern in hindiPatterns) {
                val m = pattern.find(text)
                if (m != null) {
                    candidateName = m.groupValues[1]
                    break
                }
            }
        }

        // Pattern D: Gujarati / Gujlish structures
        // e.g. "Sukrut ne friend list ma add karo", "Sukrut naamno mitra umero", "મિત્ર સુકૃતને ઉમેરો"
        if (candidateName == null) {
            val gujaratiPatterns = listOf(
                Regex("""^(.+?)\s+(?:ne\s+)?(?:friend\s+list\s+ma\s+add\s+karo|mitra\s+tarike\s+add\s+karo|dost\s+tarike\s+umero|mitra\s+tarike\s+umero|mitra\s+banavo|dost\s+banavo|mitra\s+umero|dost\s+umero)$""", RegexOption.IGNORE_CASE),
                Regex("""^(.+?)\s+naamno\s+(?:mitra|dost)\s+(?:umero|banavo)$""", RegexOption.IGNORE_CASE),
                Regex("""^(?:ek\s+)?(?:navo\s+)?(?:mitra|dost)\s+(.+?)\s+(?:umero|banavo|jodo)$""", RegexOption.IGNORE_CASE),
                Regex("""^(?:મિત્ર|દોસ્ત)\s+(.+?)\s*(?:ને)?\s*(?:ઉમેરો|બનાવો|જોડો)$"""),
                Regex("""^(.+?)\s*(?:નામનો|નામની|નામનું)?\s*(?:મિત્ર|દોસ્ત)\s*(?:તરીકે)?\s*(?:ઉમેરો|બનાવો|જોડો)$"""),
                Regex("""^(.+?)\s*ને\s*(?:મિત્ર|દોસ્ત)\s*(?:તરીકે)?\s*(?:ઉમેરો|બનાવો|જોડો)$"""),
                Regex("""^(?:એક\s+)?(?:દોસ્ત|મિત્ર|નવો\s+મિત્ર)\s+(.+?)\s*(?:જોડો|ઉમેરો|બનાવો)$""")
            )
            for (pattern in gujaratiPatterns) {
                val m = pattern.find(text)
                if (m != null) {
                    candidateName = m.groupValues[1]
                    break
                }
            }
        }

        // 3. Fallback: Systematic token-stripping if no exact regex matched
        var cleaned = candidateName ?: run {
            var temp = text
            val fallbackPhrases = listOf(
                "add a friend named", "add friend named", "create friend named", "create a friend named",
                "add a friend called", "add friend called", "create friend called",
                "add a friend", "add new friend", "create new friend", "add friend", "create friend",
                "a new friend", "new friend", "to my friends", "to friends", "in friends",
                "as a friend", "as friend", "friend list mein add karo", "friend list ma add karo",
                "naam ka naya dost jodo", "naam ka naya dost", "naam ka dost jodo", "naam ka dost",
                "ko naya dost jodo", "ko dost jodo", "naya dost jodo", "dost jodo",
                "dost ke roop me jodo", "dost ke roop mein jodo", "dost ke roop me", "dost ke roop mein",
                "naya dost banao", "dost banao", "naya dost",
                "namno navo mitra umero", "namno navo mitra", "namno mitra umero", "namno mitra",
                "navo mitra umero", "navo mitra", "mitra umero", "dost umero", "mitra banavo", "dost banavo",
                "tarike add karo", "tarike umero", "tarike jodo", "tarike",
                "નામનો નવો મિત્ર ઉમેરો", "નામનો મિત્ર ઉમેરો", "નવો મિત્ર ઉમેરો", "મિત્ર ઉમેરો",
                "દોસ્ત ઉમેરો", "દોસ્ત બનાવો", "દોસ્ત જોડો", "નવો મિત્ર", "નામનો મિત્ર",
                "નામનો", "નામની", "નામનું",
                "नाम का नया दोस्त जोड़ो", "नया दोस्त जोड़ो", "दोस्त जोड़ो", "दोस्त बनाओ", "नया दोस्त", "नाम का दोस्त",
                "नाम का", "नाम की", "नाम के",
                "named", "called", "naam ka", "naam ki", "naam ke", "naamno", "namno",
                "મિત્ર", "દોસ્ત", "દોસ્તો", "दोस्त", "मित्र", "friend", "friends"
            ).sortedByDescending { it.length }

            for (p in fallbackPhrases) {
                temp = temp.replace(Regex("""\b${Regex.escape(p)}\b""", RegexOption.IGNORE_CASE), " ")
            }
            temp
        }

        // 4. Clean particles and genitives while preserving Indic Unicode letters and matras
        cleaned = cleaned.replace(Regex("""\b(?:ka|ki|ke|ko|ne|no|ni|nu|e|thi|se|મા|માં|મેં|કો|કા|કી|કે)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{M}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val tokens = cleaned.split(" ").map { token ->
            var t = token.trim()
            for (suf in GENITIVE_SUFFIXES) {
                if (t.endsWith(suf) && t.length > suf.length) {
                    t = t.substring(0, t.length - suf.length)
                }
            }
            t
        }.filter { it.isNotBlank() && !GENITIVE_SUFFIXES.contains(it) }

        // Filter out any standalone command tokens
        val genuineNameTokens = tokens.filter { word ->
            !FRIEND_COMMAND_TOKENS.contains(word.lowercase(Locale.ROOT))
        }

        if (genuineNameTokens.isEmpty()) {
            return FriendExtractionResult(
                name = "",
                phoneNumber = phoneNumber,
                isAmbiguousOrMissing = true
            )
        }

        // Titlecase each word preserving multi-word names like "Raj Kumar"
        val formattedName = genuineNameTokens.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        if (isInvalidFriendName(formattedName)) {
            return FriendExtractionResult(
                name = "",
                phoneNumber = phoneNumber,
                isAmbiguousOrMissing = true
            )
        }

        return FriendExtractionResult(
            name = formattedName,
            phoneNumber = phoneNumber,
            isAmbiguousOrMissing = false
        )
    }

    /**
     * Extracts meaningful transaction title/description separate from category and command markers.
     */
    fun extractExpenseTitle(normalizedText: String, rawText: String, defaultCategoryName: String): ExpenseTitleExtractionResult {
        var text = normalizedText

        // 1. Strip Amounts and Currencies (Unicode-safe for Indic currency symbols and words)
        text = text.replace(Regex("""(?:₹|rs\.?|inr|rupees|rupaye|rupya|रुपये|रुपया|રૂપિયા|રુપિયા)?\s*\d+(?:\.\d+)?(?:\s*(?:k|thousand|hazar|lakh|crore))?(?:\s*(?:rupees|rupaye|rupya|रुपये|रुपया|રૂપિયા|રુપિયા|rs))?""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\d+(?:\.\d+)?"""), " ")

        // Strip standalone currency tokens
        val currencyWords = listOf("rupees", "rupaye", "rupya", "रुपये", "रुपया", "રૂપિયા", "રુપિયા", "inr", "rs")
        for (cw in currencyWords) {
            text = text.replace(cw, " ")
        }

        // 2. Strip Payment Method instructions
        val paymentPhrases = listOf(
            "paid by credit card", "paid by debit card", "paid by net banking", "paid by upi", "paid by cash", "paid by card",
            "paid via credit card", "paid via debit card", "paid via net banking", "paid via upi", "paid via cash", "paid via card",
            "using credit card", "using debit card", "using net banking", "using upi", "using cash", "using card",
            "with credit card", "with debit card", "with net banking", "with upi", "with cash", "with card",
            "through credit card", "through debit card", "through upi", "through cash",
            "credit card", "debit card", "net banking", "card thi", "upi thi", "cash thi",
            "card se", "upi se", "cash se", "રોકડથી", "યુપીઆઈથી", "કેશથી", "कैश से", "यूपीआई से",
            "by cash", "by upi", "by card", "in cash"
        ).sortedByDescending { it.length }

        for (p in paymentPhrases) {
            text = text.replace(p, " ")
        }

        // 3. Strip Payer instructions
        val payerPhrases = listOf(
            "paid by me", "paid by myself", "paid by",
            "me pay karyu", "me chukvya", "mene diya", "mene pay kiya", "mene diye",
            "મેં ચૂકવ્યા", "મેં આપ્યા", "મેં પે કર્યું", "મેં ભર્યા",
            "मैंने दिए", "मैंने दिया", "मैंने पे किया", "मैंने भरे"
        ).sortedByDescending { it.length }

        for (p in payerPhrases) {
            text = text.replace(p, " ")
        }

        // 4. Strip Split instructions
        val splitPhrases = listOf(
            "split equally with", "divided equally with", "divided equally", "split equally",
            "split with", "divide with", "between me and", "sathe split karo", "ke sath split karo"
        ).sortedByDescending { it.length }

        for (p in splitPhrases) {
            text = text.replace(p, " ")
        }

        // 5. Strip Date and Time markers
        val datePhrases = listOf(
            "today", "yesterday", "tomorrow", "aaj", "aaje", "kal", "gai kaale", "gaikaale",
            "be divas pahela", "do din pehle", "આજે", "ગઈકાલે", "કાલે", "आज", "कल"
        ).sortedByDescending { it.length }

        for (p in datePhrases) {
            text = text.replace(p, " ")
        }

        // 6. Strip Command verbs and sentence prefixes / suffixes
        val commandPhrases = listOf(
            "add an expense of", "add expense of", "add an expense", "add expense",
            "add a transaction of", "add transaction of", "add a transaction", "add transaction",
            "please add", "i have spent", "i spent", "spent", "i have paid", "i paid", "paid for",
            "record expense", "record", "create expense", "create",
            "expense of", "expense", "expenses",
            "par kharch kiye", "par kharch kiya", "kharch kiye", "kharch kiya", "kharch kare",
            "ka kharcha", "ka kharch", "kharcha", "kharch",
            "पर खर्च किए", "पर खर्च किया", "खर्च किए", "खर्च किया", "खर्च",
            "maate kharchya", "mate kharchya", "kharchya", "kharch karyo", "kharch karya",
            "no kharch", "ni pachhal", "ni paachal", "માટે ખર્ચ્યા", "ખર્ચ્યા", "ખર્ચ કર્યો", "નો ખર્ચ",
            "jodo", "likho", "umero", "lakho", "ઉમેરો", "લખો", "जोड़ो", "लिखो"
        ).sortedByDescending { it.length }

        for (p in commandPhrases) {
            val isAscii = p.all { it in 'a'..'z' || it in 'A'..'Z' || it == ' ' }
            val regex = if (isAscii) {
                Regex("""\b${Regex.escape(p)}\b""", RegexOption.IGNORE_CASE)
            } else {
                Regex("""(?<=^|[\s\p{Punct}])${Regex.escape(p)}(?=[\s\p{Punct}]|$)""", RegexOption.IGNORE_CASE)
            }
            text = text.replace(regex, " ")
        }

        // Also strip standalone command words: add, paid, spent
        val standaloneVerbs = listOf("add", "paid", "spent")
        for (v in standaloneVerbs) {
            text = text.replace(Regex("""\b${Regex.escape(v)}\b""", RegexOption.IGNORE_CASE), " ")
        }

        // 7. Strip connecting prepositions and particles
        val prepositions = listOf(
            "for", "on", "of", "in", "at",
            "ka", "ki", "ke", "par", "pe", "maate", "mate", "no", "ni", "nu",
            "માટે", "પર", "નો", "ની", "નું", "કા", "કી", "કે",
            "का", "की", "के", "पर", "के लिए", "के लिये", "ma", "maa", "mein", "me"
        ).sortedByDescending { it.length }

        for (p in prepositions) {
            val isAscii = p.all { it in 'a'..'z' || it in 'A'..'Z' || it == ' ' }
            val regex = if (isAscii) {
                Regex("""\b${Regex.escape(p)}\b""", RegexOption.IGNORE_CASE)
            } else {
                Regex("""(?<=^|[\s\p{Punct}])${Regex.escape(p)}(?=[\s\p{Punct}]|$)""", RegexOption.IGNORE_CASE)
            }
            text = text.replace(regex, " ")
        }

        // 8. Clean punctuation, collapse whitespaces
        var cleaned = text.replace(Regex("""[^\p{L}\p{M}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Handle Hindi oblique "खाने" -> "खाना"
        if (cleaned.equals("खाने", ignoreCase = true) || cleaned.startsWith("खाने ") || cleaned.equals("खाना पर", ignoreCase = true)) {
            cleaned = "खाना"
        }

        if (cleaned.isBlank() || cleaned.length < 2) {
            return ExpenseTitleExtractionResult(
                title = "",
                isMissing = true
            )
        }

        val tokensList = cleaned.split(" ").filter { it.isNotBlank() }
        if (tokensList.all { COMMAND_OR_NOISE_TOKENS.contains(it.lowercase(Locale.ROOT)) }) {
            return ExpenseTitleExtractionResult(
                title = "",
                isMissing = true
            )
        }

        // Format title preserving merchant names and multi-word phrases
        val formattedTitle = cleaned.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        return ExpenseTitleExtractionResult(
            title = formattedTitle,
            isMissing = false
        )
    }

    /**
     * Inspects friend records to safely detect any voice-created friend that contains command grammar
     * (e.g. "Add A Friend Sukrut" -> "Sukrut").
     * Returns a list of Pair(Friend, suggestedSanitizedName).
     */
    fun detectSuspiciousFriendNames(friends: List<Friend>): List<Pair<Friend, String>> {
        val suspiciousPrefixes = listOf(
            "add a friend named", "add friend named", "create friend named",
            "add a friend called", "add friend called",
            "add a friend", "add new friend", "create new friend", "add friend", "create friend",
            "a new friend", "new friend", "dost jodo", "mitra umero",
            "દોસ્ત ઉમેરો", "મિત્ર ઉમેરો", "દોસ્ત બનાવો", "દોસ્ત જોડો", "નવો મિત્ર",
            "दोस्त जोड़ो", "दोस्त बनाओ", "नया दोस्त"
        ).sortedByDescending { it.length }

        val suspiciousList = mutableListOf<Pair<Friend, String>>()

        for (friend in friends) {
            val norm = VoiceTextNormalizer.normalize(friend.name)
            for (prefix in suspiciousPrefixes) {
                if (norm.startsWith(prefix) && norm.length > prefix.length) {
                    val candidate = norm.substring(prefix.length).trim()
                    val result = extractFriendName(candidate, candidate)
                    if (result.name.isNotBlank() && result.name != friend.name) {
                        suspiciousList.add(friend to result.name)
                        break
                    }
                }
            }
        }
        return suspiciousList
    }
}

