package com.pahntd.expensetracker.ui.statistics

import com.pahntd.expensetracker.data.local.relation.MonthlyAmountSummary
import java.time.YearMonth
import java.time.ZonedDateTime

/** Number of calendar months, including the current one, covered by the monthly trend. */
const val MONTHLY_TREND_MONTHS = 12L

/** One month on the monthly trend chart. */
data class MonthlyTrendPoint(
    val yearMonth: YearMonth,
    val totalIncome: Double,
    val totalExpense: Double
)

/**
 * The monthly trend's fixed window around [now]: the current calendar month and the 11 before it,
 * as a half-open epoch-millis range. Unlike [StatisticTimeFilter.toDateRange] it never depends on
 * the Statistics time filter. Month starts go through `atStartOfDay(zone)` so DST never skews them.
 */
fun monthlyTrendRange(now: ZonedDateTime): StatisticDateRange {
    val currentMonth = YearMonth.from(now)
    val firstMonth = currentMonth.minusMonths(MONTHLY_TREND_MONTHS - 1)
    return StatisticDateRange(
        startMillis = firstMonth.atDay(1).atStartOfDay(now.zone).toInstant().toEpochMilli(),
        endMillis = currentMonth.plusMonths(1).atDay(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
    )
}

/**
 * Turns the DAO's per-month totals (oldest first, only months that have transactions) into one
 * point per calendar month from the first to the last month with data, so the chart keeps even
 * chronological spacing. A month without transactions between them gets zero income and expense;
 * no month is added before the first or after the last one with data.
 */
fun List<MonthlyAmountSummary>.toMonthlyTrendPoints(): List<MonthlyTrendPoint> {
    if (isEmpty()) return emptyList()

    val byMonth = associateBy { YearMonth.of(it.year, it.month) }
    val first = byMonth.keys.min()
    val last = byMonth.keys.max()

    return generateSequence(first) { it.plusMonths(1) }
        .takeWhile { it <= last }
        .map { month ->
            val summary = byMonth[month]
            MonthlyTrendPoint(
                yearMonth = month,
                totalIncome = summary?.totalIncome ?: 0.0,
                totalExpense = summary?.totalExpense ?: 0.0
            )
        }
        .toList()
}
