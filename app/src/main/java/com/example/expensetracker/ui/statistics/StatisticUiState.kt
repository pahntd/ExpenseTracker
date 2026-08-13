package com.example.expensetracker.ui.statistics

import com.example.expensetracker.data.local.relation.CategoryWithAmountSummary

data class StatisticUiState(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
    val expenseByCategory: List<CategoryWithAmountSummary> = emptyList(),
    val incomeByCategory: List<CategoryWithAmountSummary> = emptyList()
)
