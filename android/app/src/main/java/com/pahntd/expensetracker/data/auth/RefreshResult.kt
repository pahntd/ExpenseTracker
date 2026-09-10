package com.pahntd.expensetracker.data.auth

import com.pahntd.expensetracker.data.remote.dto.RefreshTokenResponse

/**
 * Outcome of a POST /auth/refresh attempt. Mirrors [LoginResult] / [RegisterResult] — HTTP
 * details stay inside [AuthRepository] and callers only deal with domain-level results.
 */
sealed interface RefreshResult {
    data class Success(val response: RefreshTokenResponse) : RefreshResult

    /** Backend rejected the refresh token: invalid, expired, revoked, or malformed (HTTP 401/400). */
    data object InvalidRefreshToken : RefreshResult

    /** Could not reach the server (offline, DNS, timeout, connection refused). */
    data object NetworkError : RefreshResult

    /** Anything else — unexpected status code, malformed body, etc. */
    data object UnknownError : RefreshResult
}
