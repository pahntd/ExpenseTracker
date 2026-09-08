package com.pahntd.expensetracker.data.local.relation

data class CategoryWithExpenseCount(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val expenseCount: Int
)