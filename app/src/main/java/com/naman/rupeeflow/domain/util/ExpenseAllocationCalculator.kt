package com.naman.rupeeflow.domain.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Deterministic paise-based expense allocation calculator.
 * Strictly guarantees that SUM(daily allocations) == original transaction amount in paise.
 * Never uses floating-point arithmetic.
 */
object ExpenseAllocationCalculator {

    const val MIN_DURATION_DAYS: Int = 1
    const val MAX_DURATION_DAYS: Int = 365

    data class CalculatedAllocation(
        val allocationDate: Long,
        val amountPaise: Long
    )

    /**
     * Normalizes a timestamp to the exact start (midnight) of that calendar day
     * in the specified time zone.
     */
    fun normalizeToStartOfDay(
        timestamp: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Calculates daily allocations across the specified duration.
     * Remainder paise are deterministically distributed across the earliest days.
     *
     * Example:
     * Total = 10,000 paise (₹100), Days = 3
     * Day 1: 3,334 paise (₹33.34)
     * Day 2: 3,333 paise (₹33.33)
     * Day 3: 3,333 paise (₹33.33)
     */
    fun calculateAllocations(
        amountPaise: Long,
        durationDays: Int,
        startDateMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<CalculatedAllocation> {
        val safeDuration = durationDays.coerceIn(MIN_DURATION_DAYS, MAX_DURATION_DAYS)
        val safeAmount = amountPaise.coerceAtLeast(0L)

        if (safeDuration == 1) {
            val startDay = normalizeToStartOfDay(startDateMillis, timeZone)
            return listOf(CalculatedAllocation(startDay, safeAmount))
        }

        val basePaise = safeAmount / safeDuration
        val remainderPaise = (safeAmount % safeDuration).toInt()

        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = startDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val result = ArrayList<CalculatedAllocation>(safeDuration)
        for (i in 0 until safeDuration) {
            val dayDate = cal.timeInMillis
            val dayAmount = basePaise + if (i < remainderPaise) 1L else 0L
            result.add(CalculatedAllocation(dayDate, dayAmount))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Strict invariant check: SUM(allocations) == amountPaise
        val allocatedSum = result.sumOf { it.amountPaise }
        check(allocatedSum == safeAmount) {
            "Allocation calculation error: sum ($allocatedSum paise) does not equal total ($safeAmount paise)"
        }

        return result
    }

    /**
     * Calculates inclusive duration in days between start date and end date.
     * If end date is before start date, returns 1.
     */
    fun calculateDurationDays(
        startDateMillis: Long,
        endDateMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Int {
        val startDay = normalizeToStartOfDay(startDateMillis, timeZone)
        val endDay = normalizeToStartOfDay(endDateMillis, timeZone)
        if (endDay < startDay) return 1

        val calStart = Calendar.getInstance(timeZone).apply { timeInMillis = startDay }
        val calEnd = Calendar.getInstance(timeZone).apply { timeInMillis = endDay }

        var days = 1
        while (calStart.before(calEnd)) {
            calStart.add(Calendar.DAY_OF_YEAR, 1)
            days++
            if (days >= MAX_DURATION_DAYS) break
        }
        return days.coerceIn(MIN_DURATION_DAYS, MAX_DURATION_DAYS)
    }

    /**
     * Calculates the inclusive end date for a given start date and duration in days.
     */
    fun calculateEndDate(
        startDateMillis: Long,
        durationDays: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val safeDuration = durationDays.coerceIn(MIN_DURATION_DAYS, MAX_DURATION_DAYS)
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = startDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, safeDuration - 1)
        }.timeInMillis
    }

    /**
     * Formats allocation date for preview UI.
     * Example: "19 Nov" or "19 Nov 2026"
     */
    fun formatPreviewDate(
        dateMillis: Long,
        locale: Locale = Locale.getDefault(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val sdf = SimpleDateFormat("d MMM", locale).apply {
            this.timeZone = timeZone
        }
        return sdf.format(Date(dateMillis))
    }
}

