package com.naman.rupeeflow.domain.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CurrencyUtils {
    private val indianSymbols = DecimalFormatSymbols(Locale("en", "IN")).apply {
        currencySymbol = "₹"
    }

    /**
     * Formats amount in paise to standard Indian Rupee notation (e.g. ₹1,25,450.50).
     */
    fun formatPaiseToRupees(paise: Long, includeSymbol: Boolean = true, showDecimals: Boolean = true): String {
        val isNegative = paise < 0
        val absPaise = kotlin.math.abs(paise)
        val rupees = absPaise / 100
        val remainingPaise = absPaise % 100

        val formattedIntegerPart = formatIndianInteger(rupees)
        val result = StringBuilder()
        if (isNegative) result.append("-")
        if (includeSymbol) result.append("₹")
        result.append(formattedIntegerPart)

        if (showDecimals || remainingPaise > 0) {
            result.append(".").append(String.format(Locale.US, "%02d", remainingPaise))
        }

        return result.toString()
    }

    /**
     * Indian numbering system format (e.g., 12,34,567).
     */
    fun formatIndianInteger(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val lastThree = s.substring(s.length - 3)
        var remaining = s.substring(0, s.length - 3)
        val sb = StringBuilder()
        while (remaining.length > 2) {
            val part = remaining.substring(remaining.length - 2)
            sb.insert(0, ",$part")
            remaining = remaining.substring(0, remaining.length - 2)
        }
        sb.insert(0, remaining)
        sb.append(",").append(lastThree)
        return sb.toString()
    }

    /**
     * Compact format for charts and tight badges (e.g., ₹2.5k, ₹1.2L, ₹1.5Cr).
     */
    fun formatPaiseCompact(paise: Long): String {
        val absPaise = kotlin.math.abs(paise)
        val rupees = absPaise / 100.0
        val prefix = if (paise < 0) "-₹" else "₹"

        return when {
            rupees >= 10000000 -> "$prefix${String.format(Locale.US, "%.1fCr", rupees / 10000000)}"
            rupees >= 100000 -> "$prefix${String.format(Locale.US, "%.1fL", rupees / 100000)}"
            rupees >= 1000 -> "$prefix${String.format(Locale.US, "%.1fk", rupees / 1000)}"
            else -> "$prefix${rupees.toLong()}"
        }
    }

    /**
     * Parses a user-inputted string (e.g., "1250.75" or "1250") safely into paise.
     */
    fun parseInputToPaise(input: String): Long {
        val clean = input.replace("₹", "").replace(",", "").trim()
        if (clean.isBlank()) return 0L
        return try {
            val doubleVal = clean.toDouble()
            (kotlin.math.round(doubleVal * 100.0)).toLong()
        } catch (_: Exception) {
            0L
        }
    }
}

fun Long.toRupeesString(includeSymbol: Boolean = true, showDecimals: Boolean = true): String {
    return CurrencyUtils.formatPaiseToRupees(this, includeSymbol, showDecimals)
}

fun Long.toRupeesCompact(): String {
    return CurrencyUtils.formatPaiseCompact(this)
}

object DateTimeUtils {
    fun formatDate(timestamp: Long, pattern: String = "dd MMM yyyy"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatRelativeDate(timestamp: Long): String {
        val nowCal = Calendar.getInstance()
        val targetCal = Calendar.getInstance().apply { timeInMillis = timestamp }

        val isSameYear = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
        val dayDiff = nowCal.get(Calendar.DAY_OF_YEAR) - targetCal.get(Calendar.DAY_OF_YEAR)

        return when {
            isSameYear && dayDiff == 0 -> "Today"
            isSameYear && dayDiff == 1 -> "Yesterday"
            isSameYear -> formatDate(timestamp, "dd MMM")
            else -> formatDate(timestamp, "dd MMM yyyy")
        }
    }

    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getStartOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getStartOfWeek(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
