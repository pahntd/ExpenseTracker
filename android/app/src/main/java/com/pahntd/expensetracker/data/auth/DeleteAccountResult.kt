package com.pahntd.expensetracker.data.auth

/**
 * Outcome of a DELETE /account attempt. Mirrors [LoginResult] / [RefreshResult] - HTTP details
 * stay inside [AuthRepository] and callers only deal with domain-level results.
 *
 * Only [Success] means the server confirmed the deletion. Every other outcome - including
 * [NetworkError], where the request may or may not have reached the server - must leave local
 * account data untouched.
 */
sealed interface DeleteAccountResult {
    data object Success : DeleteAccountResult

    /** Could not reach the server (offline, DNS, timeout, connection refused). */
    data object NetworkError : DeleteAccountResult

    /**
     * 401 that [com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator] could not
     * recover by refreshing - the session's tokens are no longer usable.
     */
    data object SessionExpired : DeleteAccountResult

    /**
     * 404: the token was accepted but the backend has no account for it - typically because an
     * earlier deletion succeeded but its response never arrived. Not treated as success, since a
     * 404 alone does not prove which request deleted what.
     */
    data object AccountNotFound : DeleteAccountResult

    /** 5xx: the server itself failed. The backend deletes in a single transaction, so nothing changed. */
    data object ServerError : DeleteAccountResult

    /** Anything else - unexpected status code, malformed response, etc. */
    data object UnknownError : DeleteAccountResult
}
