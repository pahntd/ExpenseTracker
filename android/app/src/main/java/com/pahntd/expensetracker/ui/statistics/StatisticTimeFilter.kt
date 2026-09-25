package com.pahntd.expensetracker.ui.statistics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Time period the Statistics screen is limited to. Only the enum [name] is persisted (see
 * [com.pahntd.expensetracker.utils.AppPreferences.KEY_STATISTIC_TIME_FILTER]); the actual date
 * range is always recalculated from the current date via [toDateRange].
 */
enum class StatisticTimeFilter(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
    ALL("All");

    companion object {
        val DEFAULT = MONTH

        /** Parses a persisted [name], falling back to [DEFAULT] when missing or unknown. */
        fun fromName(name: String?): StatisticTimeFilter =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Half-open epoch-millis range `[startMillis, endMillis)` matching `TransactionEntity.date`. */
data class StatisticDateRange(
    val startMillis: Long,
    val endMillis: Long
)

/**
 * The date range for this filter around [now], or `null` for [StatisticTimeFilter.ALL] (no
 * restriction). Boundaries are calendar-based in [now]'s zone - start of day, Monday-based ISO
 * week, first of month - resolved through `atStartOfDay(zone)`, so a DST shift never skews them
 * the way fixed `24 * 60 * 60 * 1000` arithmetic would.
 */
fun StatisticTimeFilter.toDateRange(now: ZonedDateTime): StatisticDateRange? {
    val today = now.toLocalDate()
    val (start: LocalDate, end: LocalDate) = when (this) {
        StatisticTimeFilter.DAY -> today to today.plusDays(1)
        StatisticTimeFilter.WEEK -> {
            val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            startOfWeek to startOfWeek.plusWeeks(1)
        }
        StatisticTimeFilter.MONTH -> {
            val startOfMonth = today.withDayOfMonth(1)
            startOfMonth to startOfMonth.plusMonths(1)
        }
        StatisticTimeFilter.ALL -> return null
    }
    return StatisticDateRange(
        startMillis = start.atStartOfDay(now.zone).toInstant().toEpochMilli(),
        endMillis = end.atStartOfDay(now.zone).toInstant().toEpochMilli()
    )
}
