package com.naman.rupeeflow.domain.matching

import java.util.Locale
import java.util.regex.Pattern

/**
 * Normalizes raw merchant strings, extracts UPI VPAs, and identifies canonical business entities
 * across SMS, UPI notifications, and bank statements.
 */
object MerchantNormalizer {

    private val VPA_PATTERN = Pattern.compile("(?i)\\b([a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64})\\b")

    private val NOISE_WORDS = listOf(
        "private limited", "pvt ltd", "pvt. ltd.", "pvt. ltd", "pvt", "ltd", "limited",
        "technologies", "technology", "tech", "services", "service", "solutions", "solution",
        "enterprises", "enterprise", "retail", "stores", "store", "online", "india",
        "payments", "payment", "pay", "pos", "mktp", "marketplace", "billdesk", "ccavenue",
        "razorpay", "corp", "corporation", "inc", "llp", "llc", "holdings", "holding"
    )

    private val CANONICAL_ALIASES = mapOf(
        "swiggy" to listOf("swiggy", "bundl technologies", "bundl", "swiggy food", "swiggy instamart", "swiggy dineout"),
        "zomato" to listOf("zomato", "zomato media", "zomato online", "zomato payments"),
        "amazon" to listOf("amazon", "amzn", "amazon pay", "amazon seller", "amazon india", "amzn mktp"),
        "flipkart" to listOf("flipkart", "fk payments", "flipkart internet"),
        "uber" to listOf("uber", "uber bv", "uber trip", "uber india", "uber rides"),
        "ola" to listOf("ola", "ola cabs", "ani technologies", "olacabs"),
        "bigbasket" to listOf("bigbasket", "innovative retail", "supermarket grocery"),
        "blinkit" to listOf("blinkit", "grofers", "blink commerce"),
        "zepto" to listOf("zepto", "kirana kart"),
        "netflix" to listOf("netflix", "netflix entertainment"),
        "spotify" to listOf("spotify", "spotify india"),
        "starbucks" to listOf("starbucks", "tata starbucks"),
        "mcdonalds" to listOf("mcdonalds", "mcdonald's", "hardcastle restaurants"),
        "dominos" to listOf("dominos", "domino's", "jubilant foodworks"),
        "jio" to listOf("jio", "reliance jio", "jiopay"),
        "airtel" to listOf("airtel", "bharti airtel", "airtel payments")
    )

    /**
     * Extracts UPI VPA / ID if present in raw string.
     */
    fun extractVpa(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val matcher = VPA_PATTERN.matcher(text)
        return if (matcher.find()) matcher.group(1)?.lowercase(Locale.ROOT) else null
    }

    /**
     * Normalizes a merchant name:
     * 1. Strips VPA handles if present.
     * 2. Strips noise words (ltd, pvt, technologies, etc.).
     * 3. Maps to canonical alias if known.
     * 4. Cleans special punctuation, symbols, and collapsed spaces.
     */
    fun normalize(merchant: String?): String {
        if (merchant.isNullOrBlank()) return ""

        var cleaned = merchant.lowercase(Locale.ROOT).trim()

        // If it's a VPA (e.g. swiggy@icici or swiggyupi@axisbank), extract the username part
        if (cleaned.contains("@")) {
            val vpa = extractVpa(cleaned)
            if (vpa != null) {
                cleaned = vpa.substringBefore("@")
            }
        }

        // Replace common punctuation and separators with space
        cleaned = cleaned.replace(Regex("""[*_\-/\\#:@|,]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Check canonical aliases first
        for ((canonical, aliases) in CANONICAL_ALIASES) {
            for (alias in aliases) {
                if (cleaned == alias || cleaned.startsWith("$alias ") || cleaned.endsWith(" $alias") || cleaned.contains(" $alias ")) {
                    return canonical
                }
            }
        }

        // Strip noise words
        val tokens = cleaned.split(" ").filter { it.isNotBlank() }
        val filteredTokens = tokens.filter { token ->
            !NOISE_WORDS.contains(token)
        }

        val result = if (filteredTokens.isNotEmpty()) {
            filteredTokens.joinToString(" ")
        } else {
            cleaned
        }

        // Secondary canonical match check on cleaned result
        for ((canonical, aliases) in CANONICAL_ALIASES) {
            for (alias in aliases) {
                if (result == alias || result.startsWith("$alias ") || result.endsWith(" $alias")) {
                    return canonical
                }
            }
        }

        return result.trim()
    }

    /**
     * Determines if two merchant representations refer to the same merchant entity.
     */
    fun isSameMerchant(merchantA: String?, merchantB: String?): Boolean {
        if (merchantA.isNullOrBlank() || merchantB.isNullOrBlank()) return false

        // Check VPA match
        val vpaA = extractVpa(merchantA)
        val vpaB = extractVpa(merchantB)
        if (vpaA != null && vpaB != null && vpaA.equals(vpaB, ignoreCase = true)) {
            return true
        }

        val normA = normalize(merchantA)
        val normB = normalize(merchantB)
        if (normA.isBlank() || normB.isBlank()) return false

        if (normA == normB) return true

        // Substring match with minimum length constraint to avoid false positives (e.g. "go" vs "google")
        if (normA.length >= 4 && normB.length >= 4) {
            if (normA.contains(normB) || normB.contains(normA)) {
                return true
            }
        }

        return false
    }
}
