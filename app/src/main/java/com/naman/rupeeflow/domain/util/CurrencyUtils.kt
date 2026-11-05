package com.naman.rupeeflow.domain.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CurrencyUtils {
    private val indianSymbols = DecimalFormatSymbols(Locale("en", "IN")).apply {
        currencySymbol = "₹"
    }

    /**
     * Formats amount in paise to standard Indian Rupee notation (e.g. ₹1,25,450.50).
     * Handles negative, zero, and very large amounts safely without overflow.
     */
    fun formatPaiseToRupees(paise: Long, includeSymbol: Boolean = true, showDecimals: Boolean = true): String {
        val isNegative = paise < 0
        val absPaise = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
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
        val isNegative = paise < 0
        val absPaise = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(paise)
        val rupees = absPaise / 100.0
        val prefix = if (isNegative) "-₹" else "₹"

        return when {
            rupees >= 10000000 -> "$prefix${String.format(Locale.US, "%.1fCr", rupees / 10000000)}"
            rupees >= 100000 -> "$prefix${String.format(Locale.US, "%.1fL", rupees / 100000)}"
            rupees >= 1000 -> "$prefix${String.format(Locale.US, "%.1fk", rupees / 1000)}"
            else -> "$prefix${(absPaise / 100)}"
        }
    }

    /**
     * Uses BigDecimal for exact decimal parsing to prevent binary floating point errors.
     * Strictly validates format and ensures positive amounts with at most 2 decimal places.
     */
    fun parseInputToPaiseOrNull(input: String): Long? {
        val clean = input.replace("₹", "").replace(",", "").trim()
        if (clean.isBlank()) return null
        return try {
            val bd = BigDecimal(clean)
            if (bd.scale() > 2) return null // Disallow fractional paise
            if (bd.signum() < 0) return null // Amounts must be non-negative
            val paiseBd = bd.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
            paiseBd.longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses a user-inputted string into paise, strictly rejecting invalid inputs.
     */
    fun parseInputToPaiseStrict(input: String): Long {
        return parseInputToPaiseOrNull(input)
            ?: throw IllegalArgumentException("Invalid monetary amount: '$input'")
    }

    /**
     * Parses user input safely, returning 0L if blank or invalid.
     */
    fun parseInputToPaise(input: String): Long {
        if (input.isBlank()) return 0L
        return parseInputToPaiseOrNull(input) ?: 0L
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
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(timestamp))
    }

    fun formatRelativeDate(timestamp: Long): String {
        val tz = TimeZone.getDefault()
        val loc = Locale.getDefault()
        val nowCal = Calendar.getInstance(tz, loc)
        val targetCal = Calendar.getInstance(tz, loc).apply { timeInMillis = timestamp }

        val isSameYear = nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
        val isSameDay = isSameYear && nowCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)

        val yesterdayCal = Calendar.getInstance(tz, loc).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val isYesterday = yesterdayCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                yesterdayCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)

        return when {
            isSameDay -> "Today"
            isYesterday -> "Yesterday"
            isSameYear -> formatDate(timestamp, "dd MMM")
            else -> formatDate(timestamp, "dd MMM yyyy")
        }
    }

    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getEndOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    fun getStartOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getEndOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            timeInMillis = timestamp
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    /**
     * Monday-to-Sunday weekly boundaries calculated deterministically.
     */
    fun getStartOfWeek(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            timeInMillis = timestamp
            val dayOfWeek = get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
            add(Calendar.DAY_OF_YEAR, -daysFromMonday)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun getEndOfWeek(timestamp: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            timeInMillis = getStartOfWeek(timestamp)
            add(Calendar.DAY_OF_YEAR, 6)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}

/**
 * Robust monetary and integer arithmetic preventing Long overflows.
 * Handles boundary conditions like Long.MIN_VALUE and clamps safely
 * instead of wrapping around into corrupt positive or negative values.
 */
object SafeMath {
    fun safeAdd(a: Long, b: Long): Long {
        return try {
            Math.addExact(a, b)
        } catch (_: ArithmeticException) {
            if (a > 0L) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }

    fun safeSubtract(a: Long, b: Long): Long {
        return try {
            Math.subtractExact(a, b)
        } catch (_: ArithmeticException) {
            if (a > b) Long.MAX_VALUE else Long.MIN_VALUE
        }
    }

    fun safeAbs(value: Long): Long {
        if (value == Long.MIN_VALUE) return Long.MAX_VALUE
        return kotlin.math.abs(value)
    }

    fun safeNegate(value: Long): Long {
        if (value == Long.MIN_VALUE) return Long.MAX_VALUE
        return try {
            Math.negateExact(value)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
    }
}

fun <T> Iterable<T>.safeSumOf(selector: (T) -> Long): Long {
    var sum = 0L
    for (element in this) {
        sum = SafeMath.safeAdd(sum, selector(element))
    }
    return sum
}
