package com.example.expensetracker.ui.add

import com.example.expensetracker.data.local.relation.ExpenseWithCategory

sealed interface AddExpenseEventState {
    data class Error(
        val message: String
    ) : AddExpenseEventState

    data object Success : AddExpenseEventState

    data class EditDataLoaded(
        val expense: ExpenseWithCategory
    ) : AddExpenseEventState
}