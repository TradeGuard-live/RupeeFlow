package com.naman.rupeeflow.domain.util

import java.text.SimpleDateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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
    val canNavigatePrevious: Boolean = true,
    val endMillisExclusive: Long = endMillis + 1L
)

data class MonthWeekBucket(
    val weekIndex: Int,
    val weekLabel: String,
    val calendarWeekStart: LocalDate,
    val calendarWeekEnd: LocalDate,
    val calendarWeekStartMillis: Long,
    val calendarWeekEndMillisExclusive: Long,
    val dateRangeLabel: String
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
        val zoneId = timeZone.toZoneId()
        val refDate = Instant.ofEpochMilli(referenceTime).atZone(zoneId).toLocalDate()

        val currentMonday = refDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val targetMonday = currentMonday.plusWeeks(clampedOffset.toLong())
        val targetSunday = targetMonday.plusDays(6)

        val startMillis = targetMonday.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val nextMonday = targetMonday.plusWeeks(1)
        val endMillisExclusive = nextMonday.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endMillisExclusive - 1L

        val sdf = SimpleDateFormat("dd MMM yyyy", locale).apply { this.timeZone = timeZone }
        val label = "${sdf.format(Date(startMillis))} – ${sdf.format(Date(endMillis))}"

        return PeriodRange(
            type = PeriodType.WEEK,
            offset = clampedOffset,
            startMillis = startMillis,
            endMillis = endMillis,
            label = label,
            canNavigateNext = clampedOffset < 0,
            canNavigatePrevious = true,
            endMillisExclusive = endMillisExclusive
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
        val zoneId = timeZone.toZoneId()
        val refDate = Instant.ofEpochMilli(referenceTime).atZone(zoneId).toLocalDate()
        val baseYearMonth = YearMonth.from(refDate)
        val targetYearMonth = baseYearMonth.plusMonths(clampedOffset.toLong())

        val startLocalDate = targetYearMonth.atDay(1)
        val nextMonthLocalDate = targetYearMonth.plusMonths(1).atDay(1)

        val startMillis = startLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillisExclusive = nextMonthLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endMillisExclusive - 1L

        val sdf = SimpleDateFormat("MMMM yyyy", locale).apply { this.timeZone = timeZone }
        val label = sdf.format(Date(startMillis))

        return PeriodRange(
            type = PeriodType.MONTH,
            offset = clampedOffset,
            startMillis = startMillis,
            endMillis = endMillis,
            label = label,
            canNavigateNext = clampedOffset < 0,
            canNavigatePrevious = true,
            endMillisExclusive = endMillisExclusive
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
        val zoneId = timeZone.toZoneId()
        val refDate = Instant.ofEpochMilli(referenceTime).atZone(zoneId).toLocalDate()
        val targetYear = refDate.year + clampedOffset

        val startLocalDate = LocalDate.of(targetYear, 1, 1)
        val nextYearLocalDate = LocalDate.of(targetYear + 1, 1, 1)

        val startMillis = startLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillisExclusive = nextYearLocalDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = endMillisExclusive - 1L

        val sdf = SimpleDateFormat("yyyy", locale).apply { this.timeZone = timeZone }
        val label = "Year ${sdf.format(Date(startMillis))}"

        return PeriodRange(
            type = PeriodType.YEAR,
            offset = clampedOffset,
            startMillis = startMillis,
            endMillis = endMillis,
            label = label,
            canNavigateNext = clampedOffset < 0,
            canNavigatePrevious = true,
            endMillisExclusive = endMillisExclusive
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

    /**
     * Calculates Monday-to-Sunday weekly buckets for a calendar month.
     * Week 1 starts on the Monday containing the 1st of the month.
     * The last week starts on the Monday containing or preceding the last day of the month,
     * ending on the following Sunday.
     */
    fun getMonthlyWeeks(
        yearMonth: YearMonth,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): List<MonthWeekBucket> {
        val zoneId = timeZone.toZoneId()
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()

        val firstMonday = firstDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        val buckets = mutableListOf<MonthWeekBucket>()
        var currentMonday = firstMonday
        var weekIndex = 1

        val shortMonthFormatter = DateTimeFormatter.ofPattern("MMM", locale)
        val dayWithYearFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)

        while (!currentMonday.isAfter(lastDay)) {
            val currentSunday = currentMonday.plusDays(6)
            val startMillis = currentMonday.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val nextMonday = currentMonday.plusWeeks(1)
            val endMillisExclusive = nextMonday.atStartOfDay(zoneId).toInstant().toEpochMilli()

            val dateRangeLabel = if (currentMonday.year != currentSunday.year) {
                "${currentMonday.dayOfMonth} ${currentMonday.format(shortMonthFormatter)} ${currentMonday.year} – ${currentSunday.dayOfMonth} ${currentSunday.format(shortMonthFormatter)} ${currentSunday.year}"
            } else {
                "${currentMonday.dayOfMonth} ${currentMonday.format(shortMonthFormatter)} – ${currentSunday.dayOfMonth} ${currentSunday.format(shortMonthFormatter)}"
            }

            buckets.add(
                MonthWeekBucket(
                    weekIndex = weekIndex,
                    weekLabel = "Week $weekIndex",
                    calendarWeekStart = currentMonday,
                    calendarWeekEnd = currentSunday,
                    calendarWeekStartMillis = startMillis,
                    calendarWeekEndMillisExclusive = endMillisExclusive,
                    dateRangeLabel = dateRangeLabel
                )
            )

            currentMonday = nextMonday
            weekIndex++
        }

        return buckets
    }
}
