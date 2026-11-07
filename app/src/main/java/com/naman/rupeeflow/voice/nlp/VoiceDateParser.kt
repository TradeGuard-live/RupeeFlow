package com.naman.rupeeflow.voice.nlp

import com.naman.rupeeflow.voice.model.DateRangeFilter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Natural language date parsing across English, Hindi, Gujarati, and Hinglish.
 */
object VoiceDateParser {

    private val MONTH_NAMES = mapOf(
        "january" to 1, "jan" to 1, "janvari" to 1, "જાન્યુઆરી" to 1, "जनवरी" to 1,
        "february" to 2, "feb" to 2, "farvari" to 2, "ફેબ્રુઆરી" to 2, "फरवरी" to 2,
        "march" to 3, "mar" to 3, "માર્ચ" to 3, "मार्च" to 3,
        "april" to 4, "apr" to 4, "એપ્રિલ" to 4, "अप्रैल" to 4,
        "may" to 5, "મે" to 5, "मई" to 5,
        "june" to 6, "jun" to 6, "જૂન" to 6, "जून" to 6,
        "july" to 7, "jul" to 7, "જુલાઈ" to 7, "जुलाई" to 7,
        "august" to 8, "aug" to 8, "ઓગસ્ટ" to 8, "अगस्त" to 8,
        "september" to 9, "sep" to 9, "સપ્ટેમ્બર" to 9, "सितंबर" to 9,
        "october" to 10, "oct" to 10, "ઓક્ટોબર" to 10, "अक्टूबर" to 10,
        "november" to 11, "nov" to 11, "નવેમ્બર" to 11, "नवंबर" to 11,
        "december" to 12, "dec" to 12, "ડિસેમ્બર" to 12, "दिसंबर" to 12
    )

    /**
     * Resolves an exact timestamp (in millis) for transaction creation or edits.
     * Defaults to current time if no explicit date mentioned.
     */
    fun parseTransactionDate(text: String, nowMillis: Long = System.currentTimeMillis()): Long {
        val normalized = VoiceTextNormalizer.normalize(text)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        // 1. Relative keywords
        when {
            containsAny(normalized, "yesterday", "gai kaale", "ગઈકાલે", "kal", "कल") -> {
                // In Indian expense context, "kal" / "कल" almost always refers to yesterday for past expenses
                val targetDate = today.minusDays(1)
                return targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            containsAny(normalized, "tomorrow", "aavti kaale", "કાલે") -> {
                val targetDate = today.plusDays(1)
                return targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            containsAny(normalized, "today", "aaj", "aaje", "આજે", "आज") -> {
                return nowMillis
            }
            containsAny(normalized, "day before yesterday", "parson", "परसों") -> {
                val targetDate = today.minusDays(2)
                return targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }

        // 2. Specific weekday (e.g. "last monday", "somvar", "somwar")
        val weekday = parseWeekday(normalized)
        if (weekday != null) {
            var target = today.minusDays(1)
            while (target.dayOfWeek != weekday) {
                target = target.minusDays(1)
            }
            return target.atStartOfDay(zone).toInstant().toEpochMilli()
        }

        // 3. Explicit day + month (e.g. "5 november", "5th nov", "5 nov", "નવેમ્બર 5")
        val dateMatch = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+([a-zA-Z\u0A80-\u0AFF\u0900-\u097F]+)\b""").find(normalized)
        if (dateMatch != null) {
            val day = dateMatch.groupValues[1].toIntOrNull()
            val monthStr = dateMatch.groupValues[2].lowercase()
            val month = MONTH_NAMES[monthStr]
            if (day != null && month != null && day in 1..31) {
                val currentYear = today.year
                val targetDate = LocalDate.of(currentYear, month, day)
                return targetDate.atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }

        // 4. Numeric format e.g. "5/11" or "5/11/2026"
        val numericDateMatch = Regex("""\b(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2,4}))?\b""").find(normalized)
        if (numericDateMatch != null) {
            val d = numericDateMatch.groupValues[1].toIntOrNull()
            val m = numericDateMatch.groupValues[2].toIntOrNull()
            val y = numericDateMatch.groupValues[3].toIntOrNull() ?: today.year
            val fullYear = if (y < 100) 2000 + y else y
            if (d != null && m != null && m in 1..12 && d in 1..31) {
                return LocalDate.of(fullYear, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }

        return nowMillis
    }

    /**
     * Resolves DateRangeFilter for queries (e.g. "this month", "yesterday", "last week").
     */
    fun parseDateRangeFilter(text: String): DateRangeFilter {
        val normalized = VoiceTextNormalizer.normalize(text)
        return when {
            containsAny(normalized, "today", "aaj", "aaje", "આજે", "आज") -> DateRangeFilter.TODAY
            containsAny(normalized, "yesterday", "gai kaale", "ગઈકાલે", "kal", "कल") -> DateRangeFilter.YESTERDAY
            containsAny(normalized, "this week", "is hafte", "aa athvadiye", "આ અઠવાડિયે", "इस हफ्ते") -> DateRangeFilter.THIS_WEEK
            containsAny(normalized, "last week", "pichhle hafte", "gaya athvadiye", "ગયા અઠવાડિયે", "पिछले हफ्ते") -> DateRangeFilter.LAST_WEEK
            containsAny(normalized, "this month", "is mahine", "aa mahine", "આ મહિને", "इस महीने", "current month") -> DateRangeFilter.THIS_MONTH
            containsAny(normalized, "last month", "pichhle mahine", "gaya mahine", "ગયા મહિને", "पिछले महीने") -> DateRangeFilter.LAST_MONTH
            else -> DateRangeFilter.ALL_TIME
        }
    }

    /**
     * Computes [startMillis, endMillis] bounds for a DateRangeFilter.
     */
    fun computeFilterBounds(filter: DateRangeFilter, nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        return when (filter) {
            DateRangeFilter.TODAY -> {
                val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                Pair(start, end)
            }
            DateRangeFilter.YESTERDAY -> {
                val y = today.minusDays(1)
                val start = y.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = today.atStartOfDay(zone).toInstant().toEpochMilli() - 1
                Pair(start, end)
            }
            DateRangeFilter.THIS_WEEK -> {
                val startOfWeek = today.with(DayOfWeek.MONDAY)
                val start = startOfWeek.atStartOfDay(zone).toInstant().toEpochMilli()
                Pair(start, nowMillis)
            }
            DateRangeFilter.LAST_WEEK -> {
                val lastWeekStart = today.minusWeeks(1).with(DayOfWeek.MONDAY)
                val lastWeekEnd = lastWeekStart.plusDays(6)
                val start = lastWeekStart.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = lastWeekEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                Pair(start, end)
            }
            DateRangeFilter.THIS_MONTH -> {
                val startOfMonth = today.withDayOfMonth(1)
                val start = startOfMonth.atStartOfDay(zone).toInstant().toEpochMilli()
                Pair(start, nowMillis)
            }
            DateRangeFilter.LAST_MONTH -> {
                val prevMonth = today.minusMonths(1)
                val startOfMonth = prevMonth.withDayOfMonth(1)
                val endOfMonth = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth())
                val start = startOfMonth.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = endOfMonth.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                Pair(start, end)
            }
            DateRangeFilter.ALL_TIME -> Pair(0L, Long.MAX_VALUE)
        }
    }

    private fun parseWeekday(text: String): DayOfWeek? {
        return when {
            containsAny(text, "monday", "somvar", "somwar", "સોમવાર", "सोमवार") -> DayOfWeek.MONDAY
            containsAny(text, "tuesday", "mangalvar", "mangalwar", "મંગળવાર", "मंगलवार") -> DayOfWeek.TUESDAY
            containsAny(text, "wednesday", "budhvar", "budhwar", "બુધવાર", "बुधवार") -> DayOfWeek.WEDNESDAY
            containsAny(text, "thursday", "guruvar", "guruwar", "ગુરુવાર", "गुरुवार") -> DayOfWeek.THURSDAY
            containsAny(text, "friday", "shukravar", "shukrawar", "શુક્રવાર", "शुक्रवार") -> DayOfWeek.FRIDAY
            containsAny(text, "saturday", "shanivar", "shaniwar", "શનિવાર", "शनिवार") -> DayOfWeek.SATURDAY
            containsAny(text, "sunday", "ravivar", "raviwar", "રવિવાર", "रविवार") -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun containsAny(text: String, vararg targets: String): Boolean {
        for (target in targets) {
            if (text.contains(target)) return true
        }
        return false
    }
}

