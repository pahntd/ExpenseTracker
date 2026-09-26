package com.pahntd.expensetracker.ads

/**
 * Statistics features that stay locked until the user watches a rewarded ad. Each one is unlocked
 * independently - earning a reward for one never unlocks another.
 *
 * [id] is persisted as part of a preferences key, so it must never change once shipped
 * (renaming the enum constant is fine).
 */
enum class LockedFeature(val id: String) {
    INCOME_BY_CATEGORY("income_by_category"),
    MONTHLY_TREND("income_expense_monthly_trend")
}
