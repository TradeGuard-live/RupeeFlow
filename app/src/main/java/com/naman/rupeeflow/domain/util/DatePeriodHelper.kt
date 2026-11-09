package com.naman.rupeeflow.domain.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class PeriodType {
    WEEK,
    MONTH,
    YEAR
}

data class PeriodRange(
    val type: PeriodType,
    val offset: Int,
    val startMillis: Long,
    val endMillis: Long,
    val label: String,
    val canNavigateNext: Boolean,
    val canNavigatePrevious: Boolean = true
)

/**
 * Deterministic helper for weekly, monthly, and yearly period calculations.
 * Ensures consistent Monday-Sunday weekly boundaries, exact calendar months
 * (handling 28, 29, 30, and 31 days with leap year awareness), and strict
 * future navigation prevention (offsets <= 0).
 */
object DatePeriodHelper {

    /**
     * Calculates Monday 00:00:00.000 to Sunday 23:59:59.999 for the given week offset.
     * Offset 0 = current week, -1 = last week, etc.
     * Offsets > 0 are restricted and clamped to 0 unless future reporting is enabled.
     */
    fun getWeekRange(
        offset: Int,
        referenceTime: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): PeriodRange {
        val clampedOffset = offset.coerceAtMost(0)
        val cal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = referenceTime
            val dayOfWeek = get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
            add(Calendar.DAY_OF_YEAR, -daysFromMonday + (clampedOffset * 7))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis

        val endCal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = startMillis
            add(Calendar.DAY_OF_YEAR, 6)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endMillis = endCal.timeInMillis

        val sdf = SimpleDateFormat("dd MMM yyyy", locale).apply { this.timeZone = timeZone }
        val label = "${sdf.format(Date(startMillis))} – ${sdf.format(Date(endMillis))}"

        return PeriodRange(
            type = PeriodType.WEEK,
            offset = clampedOffset,
            startMillis = startMillis,
            endMillis = endMillis,
            label = label,
            canNavigateNext = clampedOffset < 0,
            canNavigatePrevious = true
        )
    }

    /**
     * Calculates 1st of month 00:00:00.000 to last day of month 23:59:59.999.
     * Offset 0 = current month, -1 = previous month, etc.
     * Accurately handles leap years (February 29 vs 28) and December-January boundaries.
     */
    fun getMonthRange(
        offset: Int,
        referenceTime: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): PeriodRange {
        val clampedOffset = offset.coerceAtMost(0)
        val cal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = referenceTime
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, clampedOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis

        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val endCal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = startMillis
            set(Calendar.DAY_OF_MONTH, maxDay)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endMillis = endCal.timeInMillis

        val sdf = SimpleDateFormat("MMMM yyyy", locale).apply { this.timeZone = timeZone }
        val label = sdf.format(Date(startMillis))

        return PeriodRange(
            type = PeriodType.MONTH,
            offset = clampedOffset,
            startMillis = startMillis,
            endMillis = endMillis,
            label = label,
            canNavigateNext = clampedOffset < 0,
            canNavigatePrevious = true
        )
    }

    /**
     * Calculates Jan 1st 00:00:00.000 to Dec 31st 23:59:59.999 for yearly reporting.
     */
    fun getYearRange(
        offset: Int,
        referenceTime: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): PeriodRange {
        val clampedOffset = offset.coerceAtMost(0)
        val cal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = referenceTime
            set(Calendar.DAY_OF_YEAR, 1)
            add(Calendar.YEAR, clampedOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis

        val maxDayOfYear = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        val endCal = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = startMillis
            set(Calendar.DAY_OF_YEAR, maxDayOfYear)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endMillis = endCal.timeInMillis

        val sdf = SimpleDateFormat("yyyy", locale).apply { this.timeZone = timeZone }
        val label = "Year ${sdf.format(Date(startMillis))}"

        return PeriodRange(
            type = PeriodType.YEAR,
            offset = clampedOffset,
            startMillis = startMillis,
            endMillis = endMillis,
            label = label,
            canNavigateNext = clampedOffset < 0,
            canNavigatePrevious = true
        )
    }

    fun getRange(
        type: PeriodType,
        offset: Int,
        referenceTime: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): PeriodRange {
        return when (type) {
            PeriodType.WEEK -> getWeekRange(offset, referenceTime, timeZone, locale)
            PeriodType.MONTH -> getMonthRange(offset, referenceTime, timeZone, locale)
            PeriodType.YEAR -> getYearRange(offset, referenceTime, timeZone, locale)
        }
    }
}

