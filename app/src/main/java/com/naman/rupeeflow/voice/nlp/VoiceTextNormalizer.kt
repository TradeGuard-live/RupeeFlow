package com.naman.rupeeflow.voice.nlp

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes multilingual speech and text input across English, Hindi, Gujarati, and Hinglish.
 */
object VoiceTextNormalizer {

    private val DEVANAGARI_DIGITS = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    private val GUJARATI_DIGITS = mapOf(
        '૦' to '0', '૧' to '1', '૨' to '2', '૩' to '3', '૪' to '4',
        '૫' to '5', '૬' to '6', '૭' to '7', '૮' to '8', '૯' to '9'
    )

    /**
     * Complete Unicode and script normalization.
     */
    fun normalize(rawText: String): String {
        if (rawText.isBlank()) return ""

        // 1. Unicode NFKC decomposition and composition
        var text = Normalizer.normalize(rawText, Normalizer.Form.NFKC)

        // 2. Convert Devanagari and Gujarati numerals to ASCII 0-9
        val sb = java.lang.StringBuilder(text.length)
        for (ch in text) {
            when {
                DEVANAGARI_DIGITS.containsKey(ch) -> sb.append(DEVANAGARI_DIGITS[ch])
                GUJARATI_DIGITS.containsKey(ch) -> sb.append(GUJARATI_DIGITS[ch])
                else -> sb.append(ch)
            }
        }
        text = sb.toString()

        // 3. Lowercase English characters without corrupting Indian scripts
        text = text.lowercase(Locale.ROOT)

        // 4. Normalize Indian currency representations
        text = text.replace("₹", " rupees ")
            .replace("rs.", " rupees ")
            .replace("rs ", " rupees ")
            .replace("inr", " rupees ")
            .replace("रुपये", " rupees ")
            .replace("रुपया", " rupees ")
            .replace("રૂપિયા", " rupees ")
            .replace("રુપિયા", " rupees ")
            .replace("rupaye", " rupees ")
            .replace("rupya", " rupees ")

        // 5. Clean punctuation while preserving decimal points and slashes for dates
        text = text.replace(Regex("[,;:!?_~*#()|\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return text
    }

    /**
     * Tokenizes normalized text into words.
     */
    fun tokenize(normalizedText: String): List<String> {
        return normalizedText.split(" ").filter { it.isNotBlank() }
    }
}
