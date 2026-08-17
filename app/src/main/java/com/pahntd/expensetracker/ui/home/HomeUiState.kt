package com.pahntd.expensetracker.ui.home

import com.pahntd.expensetracker.data.local.relation.ExpenseWithCategory

data class HomeUiState(
    val transactions: List<ExpenseWithCategory> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val balance: Double = 0.0,
)