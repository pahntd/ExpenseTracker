package com.pahntd.expensetracker.ui.detail

sealed interface ExpenseDetailEventState {
    data class Error(
        val message: String
    ) : ExpenseDetailEventState

    data object DeleteSuccess : ExpenseDetailEventState
}