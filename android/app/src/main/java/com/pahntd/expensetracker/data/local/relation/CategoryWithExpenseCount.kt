package com.pahntd.expensetracker.data.local.relation

data class CategoryWithExpenseCount(
    val categoryId: String,
    val name: String,
    val icon: String,
    val expenseCount: Int
)