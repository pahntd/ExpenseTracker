package com.example.expensetracker.ui.add

sealed interface AddExpenseEventState {
    data class Error(
        val message: String
    ) : AddExpenseEventState

    data object Success : AddExpenseEventState
}