package com.example.expensetracker.ui.detail

import androidx.core.app.NotificationCompat.MessagingStyle.Message

sealed interface ExpenseDetailEventState {
    data class Error(
        val message: String
    ) : ExpenseDetailEventState

    data object DeleteSuccess : ExpenseDetailEventState
}