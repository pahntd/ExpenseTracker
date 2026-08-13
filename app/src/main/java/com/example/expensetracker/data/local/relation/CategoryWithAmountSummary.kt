package com.example.expensetracker.data.local.relation

data class CategoryWithAmountSummary(
    val categoryId: Long,
    val categoryName: String,
    val icon: String,
    val totalAmount: Double
)
