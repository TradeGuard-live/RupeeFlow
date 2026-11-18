package com.naman.rupeeflow.ui.screens.transactions

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TransactionDateGroup(
    val heading: String,
    val dateEpochMillis: Long,
    val items: List<TransactionListItem>
) {
    val totalCount: Int get() = items.size
}

object TransactionDateGrouper {

    fun getStartOfCalendarDay(
        timestamp: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): Long {
        return Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun isToday(
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): Boolean {
        val calTarget = Calendar.getInstance(timeZone, locale).apply { timeInMillis = timestamp }
        val calNow = Calendar.getInstance(timeZone, locale).apply { timeInMillis = now }
        return calTarget.get(Calendar.ERA) == calNow.get(Calendar.ERA) &&
                calTarget.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calTarget.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
    }

    fun isYesterday(
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): Boolean {
        val calTarget = Calendar.getInstance(timeZone, locale).apply { timeInMillis = timestamp }
        val calYesterday = Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return calTarget.get(Calendar.ERA) == calYesterday.get(Calendar.ERA) &&
                calTarget.get(Calendar.YEAR) == calYesterday.get(Calendar.YEAR) &&
                calTarget.get(Calendar.DAY_OF_YEAR) == calYesterday.get(Calendar.DAY_OF_YEAR)
    }

    fun formatDateHeading(
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): String {
        return when {
            isToday(timestamp, now, timeZone, locale) -> "Today"
            isYesterday(timestamp, now, timeZone, locale) -> "Yesterday"
            else -> {
                val sdf = SimpleDateFormat("d MMMM yyyy", locale).apply {
                    this.timeZone = timeZone
                }
                sdf.format(Date(timestamp))
            }
        }
    }

    fun getItemEffectiveDate(item: TransactionListItem): Long {
        return when (item) {
            is TransactionListItem.Individual -> item.transaction.transactionDate
            is TransactionListItem.TripCard -> item.latestDate
        }
    }

    val itemComparator = Comparator<TransactionListItem> { a, b ->
        val dateA = getItemEffectiveDate(a)
        val dateB = getItemEffectiveDate(b)
        if (dateA != dateB) {
            dateB.compareTo(dateA) // Newest time first
        } else {
            val idA = when (a) {
                is TransactionListItem.Individual -> a.transaction.id
                is TransactionListItem.TripCard -> a.expenseGroupId
            }
            val idB = when (b) {
                is TransactionListItem.Individual -> b.transaction.id
                is TransactionListItem.TripCard -> b.expenseGroupId
            }
            if (idA != idB) {
                idB.compareTo(idA) // Higher ID first for deterministic ordering
            } else {
                a.javaClass.name.compareTo(b.javaClass.name)
            }
        }
    }

    fun groupItemsByDate(
        items: List<TransactionListItem>,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.getDefault()
    ): List<TransactionDateGroup> {
        if (items.isEmpty()) return emptyList()

        val grouped = items.groupBy { item ->
            getStartOfCalendarDay(getItemEffectiveDate(item), timeZone, locale)
        }

        return grouped.entries
            .sortedByDescending { it.key } // Newest date first
            .map { (startOfDayMillis, groupItems) ->
                val representativeTimestamp = getItemEffectiveDate(groupItems.first())
                val heading = formatDateHeading(representativeTimestamp, now, timeZone, locale)
                TransactionDateGroup(
                    heading = heading,
                    dateEpochMillis = startOfDayMillis,
                    items = groupItems.sortedWith(itemComparator)
                )
            }
    }
}
