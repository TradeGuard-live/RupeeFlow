package com.naman.rupeeflow.voice.nlp

import com.naman.rupeeflow.domain.model.*

sealed class FriendMatchResult {
    data class Single(val friend: Friend) : FriendMatchResult()
    data class Ambiguous(val queriedName: String, val matches: List<Friend>) : FriendMatchResult()
    data object None : FriendMatchResult()
}

/**
 * Resolves entities (Friends, Groups, Categories, Payment Methods)
 * against local Room database models.
 */
object VoiceEntityResolver {

    /**
     * Resolves a friend name against existing friends list.
     * Handles exact names, first names, aliases, and Indian script variations.
     */
    fun resolveFriend(nameQuery: String, existingFriends: List<Friend>): FriendMatchResult {
        val cleanQuery = VoiceTextNormalizer.normalize(nameQuery).trim()
        if (cleanQuery.isBlank()) return FriendMatchResult.None

        // 1. Exact match (case-insensitive)
        val exactMatches = existingFriends.filter {
            VoiceTextNormalizer.normalize(it.name).equals(cleanQuery, ignoreCase = true)
        }
        if (exactMatches.size == 1) return FriendMatchResult.Single(exactMatches[0])
        if (exactMatches.size > 1) return FriendMatchResult.Ambiguous(cleanQuery, exactMatches)

        // 2. First name match: "Jay Patel" matched by "Jay"
        val firstNameMatches = existingFriends.filter {
            val friendFirstName = VoiceTextNormalizer.normalize(it.name).split(" ").firstOrNull() ?: ""
            friendFirstName.equals(cleanQuery, ignoreCase = true)
        }
        if (firstNameMatches.size == 1) return FriendMatchResult.Single(firstNameMatches[0])
        if (firstNameMatches.size > 1) return FriendMatchResult.Ambiguous(cleanQuery, firstNameMatches)

        // 3. Substring / Script variation match (e.g. transliteration variations)
        val partialMatches = existingFriends.filter {
            val normFriend = VoiceTextNormalizer.normalize(it.name)
            normFriend.contains(cleanQuery) || cleanQuery.contains(normFriend) || isPhoneticMatch(cleanQuery, normFriend)
        }
        if (partialMatches.size == 1) return FriendMatchResult.Single(partialMatches[0])
        if (partialMatches.size > 1) return FriendMatchResult.Ambiguous(cleanQuery, partialMatches)

        return FriendMatchResult.None
    }

    /**
     * Resolves a friend group against the database.
     */
    fun resolveGroup(groupQuery: String, existingGroups: List<FriendGroup>): FriendGroup? {
        val cleanQuery = VoiceTextNormalizer.normalize(groupQuery).trim()
        if (cleanQuery.isBlank()) return null

        // 1. Exact match
        val exact = existingGroups.firstOrNull {
            VoiceTextNormalizer.normalize(it.name).equals(cleanQuery, ignoreCase = true)
        }
        if (exact != null) return exact

        // 2. Partial / contains match: e.g. "Hostel" matches "Hostel Friends"
        return existingGroups.firstOrNull {
            val normGroup = VoiceTextNormalizer.normalize(it.name)
            normGroup.contains(cleanQuery) || cleanQuery.contains(normGroup)
        }
    }

    /**
     * Finds which friend groups are referenced in a voice command.
     */
    fun findReferencedGroup(text: String, existingGroups: List<FriendGroup>): FriendGroup? {
        val normalized = VoiceTextNormalizer.normalize(text)
        for (group in existingGroups) {
            val normGroup = VoiceTextNormalizer.normalize(group.name)
            if (normalized.contains(normGroup)) return group
            // Also check first word of group (e.g. "Hostel" for "Hostel Friends")
            val firstWord = normGroup.split(" ").firstOrNull() ?: ""
            if (firstWord.length >= 4 && normalized.contains(firstWord)) return group
        }
        return null
    }

    /**
     * Finds all friends explicitly mentioned in a text.
     */
    fun findReferencedFriends(text: String, existingFriends: List<Friend>): List<Friend> {
        val normalized = VoiceTextNormalizer.normalize(text)
        val matchedFriends = mutableListOf<Friend>()

        for (friend in existingFriends) {
            val normFriend = VoiceTextNormalizer.normalize(friend.name)
            val firstName = normFriend.split(" ").firstOrNull() ?: ""

            val matches = normalized.contains(normFriend) ||
                    (firstName.length >= 3 && containsWord(normalized, firstName)) ||
                    isScriptReferenced(normalized, friend.name)

            if (matches && !matchedFriends.contains(friend)) {
                matchedFriends.add(friend)
            }
        }
        return matchedFriends
    }

    /**
     * Resolves the payer from the voice command.
     * Returns null for ME, or friendId for a friend.
     */
    fun resolvePayer(text: String, referencedFriends: List<Friend>): Pair<Long?, String> {
        val normalized = VoiceTextNormalizer.normalize(text)

        // Check if a friend paid: e.g. "Jay paid", "paid by Jay", "Jay ne bhara", "Jay e chukavya", "Jay ne pay kiya", "Jay ne diye"
        for (friend in referencedFriends) {
            val normName = VoiceTextNormalizer.normalize(friend.name)
            val firstName = normName.split(" ").firstOrNull() ?: ""
            val friendPatterns = listOf(
                "paid by $normName", "paid by $firstName",
                "$normName paid", "$firstName paid",
                "$normName gave", "$firstName gave",
                "$normName ne pay", "$firstName ne pay",
                "$normName ne bhara", "$firstName ne bhara",
                "$normName ne diya", "$firstName ne diya",
                "$normName ne diye", "$firstName ne diye",
                "$normName e chukavya", "$firstName e chukavya",
                "$normName e bharya", "$firstName e bharya",
                "$normName e pay", "$firstName e pay",
                "જયે", "માનવે", "રાહુલે"
            )
            for (p in friendPatterns) {
                if (normalized.contains(p)) {
                    return Pair(friend.id, friend.name)
                }
            }

            // Check if friend name + ne/e is present with payment verbs
            val hasPaidVerb = containsAny(normalized, "paid", "pay kiya", "pay karyu", "diye", "chukavya", "bhara", "दिए", "दिया", "ચૂકવ્યા")
            if (hasPaidVerb && isFriendPaidSubject(normalized, friend.name)) {
                return Pair(friend.id, friend.name)
            }
        }

        // Check explicit "paid by me" or "i paid"
        val mePatterns = listOf(
            "paid by me", "i paid", "maine bhara", "maine diya", "me bhara", "me chukavya", "મેં ચૂકવ્યા", "मैंने भरा"
        )
        for (p in mePatterns) {
            if (normalized.contains(p)) {
                return Pair(null, "Me")
            }
        }

        // Default to "Me" if not specified otherwise
        return Pair(null, "Me")
    }

    private fun isFriendPaidSubject(text: String, friendName: String): Boolean {
        val n = friendName.lowercase()
        return text.contains("$n ne") || text.contains("$n e") || text.contains("$n paid") ||
                (n == "jay" && (text.contains("जय ने") || text.contains("જયે") || text.contains("જય એ"))) ||
                (n == "manav" && (text.contains("मानव ने") || text.contains("માનવે") || text.contains("માનવ એ"))) ||
                (n == "priya" && (text.contains("प्रिया ने") || text.contains("પ્રિયા એ"))) ||
                (n == "rahul" && (text.contains("राहुल ने") || text.contains("રાહુલે") || text.contains("રાહુલ એ")))
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean {
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * Resolves Category from text keywords.
     */
    fun resolveCategory(text: String, existingCategories: List<Category>): Category {
        val normalized = VoiceTextNormalizer.normalize(text)

        val foodKeywords = listOf("food", "dining", "dinner", "lunch", "breakfast", "swiggy", "zomato", "restaurant", "cafe", "khana", "jamvanu", "ખાવાનું", "खाना")
        val travelKeywords = listOf("train", "ticket", "travel", "auto", "cab", "uber", "ola", "metro", "bus", "petrol", "fuel", "flight", "taxi", "વાહન", "ટિકિટ", "किराया")
        val groceryKeywords = listOf("grocery", "groceries", "supermarket", "blinkit", "zepto", "instamart", "kirana", "milk", "vegetables", "sabji", "શાકભાજી", "सब्जी")
        val rentKeywords = listOf("rent", "housing", "flat", "room", "maintenance", "pg", "भाड़ा", "ભાડું")
        val shoppingKeywords = listOf("shopping", "clothes", "amazon", "flipkart", "mall", "shoes", "ખરીદી", "खरीदारी")
        val entertainmentKeywords = listOf("movie", "cinema", "tickets", "netflix", "prime", "hotstar", "game", "ફિલ્મ", "सिनेमा")

        fun matchesCategory(keywords: List<String>): Boolean {
            return keywords.any { normalized.contains(it) }
        }

        val matchedName = when {
            matchesCategory(foodKeywords) -> "Food"
            matchesCategory(travelKeywords) -> "Travel & Transport"
            matchesCategory(groceryKeywords) -> "Groceries"
            matchesCategory(rentKeywords) -> "Rent & Housing"
            matchesCategory(shoppingKeywords) -> "Shopping"
            matchesCategory(entertainmentKeywords) -> "Entertainment"
            else -> "Other"
        }

        return existingCategories.firstOrNull { it.name.contains(matchedName, ignoreCase = true) }
            ?: existingCategories.firstOrNull { it.name.contains("Other", ignoreCase = true) }
            ?: existingCategories.firstOrNull()
            ?: Category(id = 1L, name = "General", iconName = "category", colorHex = "#3B82F6")
    }

    /**
     * Resolves PaymentMethod from keywords.
     */
    fun resolvePaymentMethod(text: String): PaymentMethod {
        val normalized = VoiceTextNormalizer.normalize(text)
        return when {
            normalized.contains("cash") || normalized.contains("rokaḍ") || normalized.contains("nakad") || normalized.contains("નકદ") || normalized.contains("नकद") -> PaymentMethod.CASH
            normalized.contains("credit card") || normalized.contains("ક્રેડિટ") || normalized.contains("क्रेडिट") -> PaymentMethod.CREDIT_CARD
            normalized.contains("card") || normalized.contains("debit card") || normalized.contains("કાર્ડ") || normalized.contains("कार्ड") -> PaymentMethod.DEBIT_CARD
            normalized.contains("net banking") || normalized.contains("netbanking") || normalized.contains("bank transfer") -> PaymentMethod.NET_BANKING
            else -> PaymentMethod.UPI // Default in India
        }
    }

    private fun containsWord(text: String, word: String): Boolean {
        return text.split(" ").any { it.equals(word, ignoreCase = true) }
    }

    private fun isPhoneticMatch(q: String, target: String): Boolean {
        // Simple letter replacement / soundex approximation for common Indian name variations
        val cleanQ = q.replace("v", "w").replace("ee", "i").replace("oo", "u")
        val cleanT = target.replace("v", "w").replace("ee", "i").replace("oo", "u")
        return cleanT.contains(cleanQ) || cleanQ.contains(cleanT)
    }

    private fun isScriptReferenced(text: String, name: String): Boolean {
        // Map common Indian script spellings
        val scriptAliases = mapOf(
            "jay" to listOf("જય", "જયે", "जय"),
            "manav" to listOf("માનવ", "માનવે", "मानव"),
            "aarav" to listOf("આરવ", "આરવે", "आरव"),
            "kunal" to listOf("કુણાલ", "કુણાલે", "कुणाल"),
            "priya" to listOf("પ્રિયા", "પ્રિયાએ", "प्रिया"),
            "rahul" to listOf("રાહુલ", "રાહુલે", "राहुल")
        )
        val aliases = scriptAliases[name.lowercase()] ?: return false
        return aliases.any { alias ->
            text.contains(alias) || text.contains(alias + "ે") || text.contains(alias + "એ") || text.contains(alias + " ને") || text.contains(alias + " ने")
        }
    }
}
