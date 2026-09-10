package com.pahntd.expensetracker.data.auth

import com.pahntd.expensetracker.data.remote.dto.LoginResponse

sealed interface LoginResult {
    data class Success(val response: LoginResponse) : LoginResult

    /** Server rejected the credentials (see note in [AuthRepository.login] about the status code). */
    data object InvalidCredentials : LoginResult

    /** Could not reach the server (offline, DNS, timeout, connection refused). */
    data object NetworkError : LoginResult

    /** Anything else — unexpected status code, malformed body, etc. */
    data object UnknownError : LoginResult
}