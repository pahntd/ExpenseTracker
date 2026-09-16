package com.pahntd.expensetracker.data.remote.error

/**
 * Application-level classification of a failed network operation. Deliberately coarse: it answers
 * *what kind* of failure this is, not what to do about it. Retry/backoff, logout, and navigation
 * decisions belong to whichever layer calls the repository (sync, WorkManager, ViewModels/UI) -
 * never to this type or to [toAppError].
 */
sealed interface AppError {

    /** No usable connection: offline, DNS failure, timeout, connection refused, etc. */
    data object Network : AppError

    /**
     * The request came back 401 and
     * [AuthAuthenticator][com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator]
     * could not recover it (no refresh token, or the refresh call itself failed) - the session's
     * tokens are no longer usable. A 401 that the authenticator refreshed and retried
     * successfully never surfaces here; only the final, unrecoverable 401 does.
     */
    data object Unauthorized : AppError

    /** Any other 4xx: bad request, validation failure, not found, conflict, etc. */
    data class Client(val code: Int) : AppError

    /** 5xx: the server itself failed. */
    data class Server(val code: Int) : AppError

    /** Anything that doesn't fit above: unexpected exceptions, parsing/data failures. */
    data class Unknown(val cause: Throwable? = null) : AppError
}
