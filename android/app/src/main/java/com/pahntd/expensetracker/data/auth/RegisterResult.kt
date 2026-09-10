package com.pahntd.expensetracker.data.auth

import com.pahntd.expensetracker.data.remote.dto.RegisterResponse

/**
 * Outcome of a register attempt. Mirrors
 * [LoginResult] — keeps Retrofit/HTTP details
 * inside the repository so the ViewModel only deals with domain-level results.
 */
sealed interface RegisterResult {
    data class Success(val response: RegisterResponse) : RegisterResult

    /** Server rejected the email as already registered (see note in AuthRepository.register). */
    data object EmailAlreadyExists : RegisterResult

    /** Could not reach the server (offline, DNS, timeout, connection refused). */
    data object NetworkError : RegisterResult

    /** Anything else — unexpected status code, malformed body, etc. */
    data object UnknownError : RegisterResult
}