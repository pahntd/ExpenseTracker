package com.pahntd.expensetracker.ui.login

import com.pahntd.expensetracker.data.remote.dto.LoginResponse

sealed interface LoginEvent {
    data class Success(
        val response: LoginResponse
    ) : LoginEvent

    data class Error(
        val message: String
    ) : LoginEvent

    /** 429 on login. The Fragment turns it into a localized message (with the wait, if known). */
    data class RateLimited(
        val retryAfterSeconds: Long?
    ) : LoginEvent
}
