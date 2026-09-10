package com.pahntd.expensetracker.ui.login

import com.pahntd.expensetracker.data.remote.dto.LoginResponse

sealed interface LoginEvent {
    data class Success(
        val response: LoginResponse
    ) : LoginEvent

    data class Error(
        val message: String
    ) : LoginEvent
}
