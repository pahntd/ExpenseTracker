package com.pahntd.expensetracker.ui.register

import com.pahntd.expensetracker.data.remote.dto.RegisterResponse

sealed interface RegisterEvent {
    /** Registration succeeded. The user is sent back to Login (no auto-login). */
    data class Success(
        val response: RegisterResponse
    ) : RegisterEvent

    /** One-time API/operation error only — never used for field validation. */
    data class Error(
        val message: String
    ) : RegisterEvent

    /** 429 on register. The Fragment turns it into a localized message (with the wait, if known). */
    data class RateLimited(
        val retryAfterSeconds: Long?
    ) : RegisterEvent
}
