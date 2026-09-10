package com.pahntd.expensetracker.ui.register

sealed interface RegisterEvent {
    data object Success : RegisterEvent

    data class Error(
        val message: String
    ) : RegisterEvent
}
