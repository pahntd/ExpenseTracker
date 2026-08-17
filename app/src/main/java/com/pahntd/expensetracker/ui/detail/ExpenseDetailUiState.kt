package com.pahntd.expensetracker.ui.detail

import com.pahntd.expensetracker.data.local.relation.ExpenseWithCategory

data class ExpenseDetailUiState(
    val expense: ExpenseWithCategory? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)