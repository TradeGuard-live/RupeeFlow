package com.naman.rupeeflow.voice.nlp

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Extracts and parses financial amounts into integer paise (₹1 = 100 paise)
 * across English, Hindi, Gujarati, and Hinglish.
 */
object VoiceNumberParser {

    private val NUMBER_WORDS = mapOf(
        // English
        "zero" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L,
        "five" to 5L, "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L,
        "ten" to 10L, "twenty" to 20L, "thirty" to 30L, "forty" to 40L,
        "fifty" to 50L, "sixty" to 60L, "seventy" to 70L, "eighty" to 80L,
        "ninety" to 90L, "hundred" to 100L, "thousand" to 1000L,

        // Hindi (Devanagari)
        "शून्य" to 0L, "एक" to 1L, "दो" to 2L, "तीन" to 3L, "चार" to 4L,
        "पांच" to 5L, "पाँच" to 5L, "छह" to 6L, "सात" to 7L, "आठ" to 8L,
        "नौ" to 9L, "दस" to 10L, "बीस" to 20L, "तीस" to 30L, "चालीस" to 40L,
        "पचास" to 50L, "सौ" to 100L, "हजार" to 1000L, "हज़ार" to 1000L,
        "लाख" to 100000L, "करोड़" to 10000000L,
        "डेढ़ सौ" to 150L, "ढाई सौ" to 250L, "साढ़े तीन सौ" to 350L,
        "डेढ़ हजार" to 1500L, "ढाई हजार" to 2500L,

        // Gujarati
        "શૂન્ય" to 0L, "એક" to 1L, "બે" to 2L, "ત્રણ" to 3L, "ચાર" to 4L,
        "પાંચ" to 5L, "છ" to 6L, "સાત" to 7L, "આઠ" to 8L, "નવ" to 9L,
        "દસ" to 10L, "વીસ" to 20L, "ત્રીસ" to 30L, "ચાલીસ" to 40L,
        "પચાસ" to 50L, "સો" to 100L, "એકસો" to 100L, "બસો" to 200L,
        "ત્રણસો" to 300L, "ચારસો" to 400L, "પાંચસો" to 500L, "હજાર" to 1000L,
        "દોઢસો" to 150L, "અઢીસો" to 250L, "સાડા ત્રણસો" to 350L,
        "લાખ" to 100000L, "કરોડ" to 10000000L,

        // Romanized Hindi / Gujarati
        "sau" to 100L, "so" to 100L, "ek sau" to 100L, "do sau" to 200L,
        "teen sau" to 300L, "char sau" to 400L, "paanch sau" to 500L,
        "che sau" to 600L, "saat sau" to 700L, "aath sau" to 800L, "nau sau" to 900L,
        "tran so" to 300L, "be so" to 200L, "panch so" to 500L,
        "hazar" to 1000L, "hajaar" to 1000L,
        "dedh sau" to 150L, "dhai sau" to 250L, "saade teen sau" to 350L,
        "dedh hazar" to 1500L, "dhai hazar" to 2500L,
        "lakh" to 100000L, "lac" to 100000L, "crore" to 10000000L, "cr" to 10000000L
    )

    /**
     * Extracts amount in integer paise from text.
     * Returns null if no amount is found.
     */
    fun parseAmountPaise(text: String): Long? {
        val normalized = VoiceTextNormalizer.normalize(text)

        // 1. Check for compound phrases like "डेढ़ सौ", "dhai sau", "દોઢસો", "અઢીસો"
        for ((word, value) in NUMBER_WORDS) {
            if (word.contains(" ") && normalized.contains(word)) {
                return value * 100L
            }
        }

        // 2. Regex for numbers with magnitude: e.g. "1.5k", "2.5 lakh", "1 crore", "500", "300.50"
        val regex = Regex("""(?:\b|₹)\s*(\d+(?:\.\d+)?)\s*(k|thousand|hazar|hajaar|हजार|હજાર|lakh|lac|लाख|લાખ|crore|cr|करोड़|કરોડ)?(?:\s*(?:rupees|रुपये|રૂપિયા|rs))?\b""")
        val match = regex.find(normalized)
        if (match != null) {
            val numStr = match.groupValues[1]
            val suffix = match.groupValues[2].lowercase()
            val baseVal = BigDecimal(numStr)

            val multiplier = when (suffix) {
                "k", "thousand", "hazar", "hajaar", "हजार", "હજાર" -> BigDecimal("1000")
                "lakh", "lac", "लाख", "લાખ" -> BigDecimal("100000")
                "crore", "cr", "करोड़", "કરોડ" -> BigDecimal("10000000")
                else -> BigDecimal("1")
            }

            val totalRupees = baseVal.multiply(multiplier)
            val paise = totalRupees.multiply(BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).toLong()
            if (paise > 0L) return paise
        }

        // 3. Check standalone number words: "तीन सौ", "પાંચસો", "five hundred"
        val words = normalized.split(" ")
        for (i in words.indices) {
            val word = words[i]
            // check single word match
            val single = NUMBER_WORDS[word]
            if (single != null && single >= 10L) {
                // If preceded by a small multiplier (e.g., "three hundred", "teen sau")
                if (i > 0) {
                    val prev = NUMBER_WORDS[words[i - 1]]
                    if (prev != null && prev in 1..9) {
                        return (prev * single) * 100L
                    }
                }
                return single * 100L
            }
        }

        return null
    }

    /**
     * Extracts all amounts in paise from a text.
     */
    fun parseAllAmountsPaise(text: String): List<Long> {
        val normalized = VoiceTextNormalizer.normalize(text)
        val regex = Regex("""(?:\b|₹)\s*(\d+(?:\.\d+)?)\s*(k|thousand|hazar|hajaar|हजार|હજાર|lakh|lac|लाख|લાખ|crore|cr|करोड़|કરોડ)?(?:\s*(?:rupees|रुपये|રૂપિયા|rs))?\b""")
        val results = mutableListOf<Long>()

        for (match in regex.findAll(normalized)) {
            val numStr = match.groupValues[1]
            val suffix = match.groupValues[2].lowercase()
            val baseVal = try { BigDecimal(numStr) } catch (_: Exception) { continue }
            val multiplier = when (suffix) {
                "k", "thousand", "hazar", "hajaar", "हजार", "હજાર" -> BigDecimal("1000")
                "lakh", "lac", "लाख", "લાખ" -> BigDecimal("100000")
                "crore", "cr", "करोड़", "કરોડ" -> BigDecimal("10000000")
                else -> BigDecimal("1")
            }
            val paise = baseVal.multiply(multiplier).multiply(BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).toLong()
            if (paise > 0L) results.add(paise)
        }
        return results
    }
}
