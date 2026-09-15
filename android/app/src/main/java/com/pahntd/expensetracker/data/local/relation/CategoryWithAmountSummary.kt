package com.pahntd.expensetracker.data.local.relation

data class CategoryWithAmountSummary(
    val categoryId: String,
    val categoryName: String,
    val icon: String,
    val totalAmount: Double
)
