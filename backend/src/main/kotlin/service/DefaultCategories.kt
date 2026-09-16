package com.pahntd.expensetracker.service

/** Canonical set of categories provisioned for every newly registered user. */
object DefaultCategories {
    val DEFINITIONS: List<Pair<String, String>> = listOf(
        "Food" to "ic_food",
        "Transport" to "ic_transport",
        "Shopping" to "ic_shopping",
        "Salary" to "ic_salary",
        "Entertainment" to "ic_entertainment",
        "Education" to "ic_education",
        "Health" to "ic_health",
        "Other" to "ic_other"
    )
}
