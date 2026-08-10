package com.example.expensetracker.ui.detail

import com.example.expensetracker.data.local.relation.ExpenseWithCategory

data class ExpenseDetailUiState(
    val expense: ExpenseWithCategory? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)