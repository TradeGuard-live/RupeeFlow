package com.naman.rupeeflow.voice.nlp

import com.naman.rupeeflow.voice.model.VoiceLanguage

/**
 * Fully on-device language detector.
 * Identifies native Gujarati, native Hindi (Devanagari), code-switched Latin Hinglish,
 * and English without requiring manual user selection or network access.
 */
object VoiceLanguageDetector {

    private val gujaratiLatinLexicon = setOf(
        "karyo", "karya", "karo", "karyu", "karvu", "che", "chhe", "chho", "levana", "aapya", "aapo",
        "aavya", "aapvana", "sathe", "mara", "mari", "maro", "maru", "tamne", "potano", "nathi",
        "mitra", "mitro", "grup", "umero", "bhojan", "joda", "hisaab", "badha", "ketla", "ketlo",
        "ketli", "aaje", "kaale", "gai", "paachhu", "pachhi", "kharidya", "vahecho", "kone",
        "mane", "thi", "naakho", "rokad", "sudharo", "rupya", "rupiya", "tarike", "namnu", "maate"
    )

    private val hindiLatinLexicon = setOf(
        "diye", "diya", "de", "lene", "lena", "hai", "hain", "karo", "kiya", "mera", "mere", "meri",
        "humne", "banao", "banaye", "kitna", "kitne", "kitni", "hua", "hue", "rupaye", "rupay",
        "paisa", "paise", "dost", "dosto", "jodo", "nikal", "nikalo", "hatao", "hata", "kharcha",
        "kharch", "bhai", "aaj", "kal", "pichhle", "waale", "liye", "bheje", "laotaye", "lautaye",
        "batado", "batao", "kiska", "kisne", "sabka", "roopay", "udhar", "joda", "se"
    )

    fun detect(text: String): VoiceLanguage {
        if (text.isBlank()) return VoiceLanguage.ENGLISH

        var gujaratiCharCount = 0
        var devanagariCharCount = 0
        var latinCharCount = 0

        for (ch in text) {
            when {
                ch in '\u0A80'..'\u0AFF' -> gujaratiCharCount++
                ch in '\u0900'..'\u097F' -> devanagariCharCount++
                ch.isLetter() -> latinCharCount++
            }
        }

        // 1. Script-based detection
        if (gujaratiCharCount > 0 && gujaratiCharCount >= devanagariCharCount) {
            return VoiceLanguage.GUJARATI
        }
        if (devanagariCharCount > 0 && devanagariCharCount > gujaratiCharCount) {
            return VoiceLanguage.HINDI
        }

        // 2. Vocabulary analysis for Romanized text
        val tokens = text.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotBlank() }
        var gujaratiScore = 0
        var hindiScore = 0

        for (token in tokens) {
            if (gujaratiLatinLexicon.contains(token)) gujaratiScore++
            if (hindiLatinLexicon.contains(token)) hindiScore++
        }

        if (gujaratiScore > 0 && gujaratiScore >= hindiScore) {
            return VoiceLanguage.GUJARATI
        }
        if (hindiScore > 0) {
            return VoiceLanguage.HINGLISH
        }

        return VoiceLanguage.ENGLISH
    }
}

