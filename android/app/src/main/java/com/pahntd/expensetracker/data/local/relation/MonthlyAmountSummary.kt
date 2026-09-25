package com.pahntd.expensetracker.data.local.relation

/**
 * Income and expense totals of one calendar month, aggregated in Room. [month] is 1-12, and both
 * [year] and [month] are in the device's local time zone.
 */
data class MonthlyAmountSummary(
    val year: Int,
    val month: Int,
    val totalIncome: Double,
    val totalExpense: Double
)
