package com.pahntd.expensetracker.data.sync

import com.pahntd.expensetracker.data.remote.error.AppError

/**
 * Outcome of a single push (one Create/Update/Delete call for one Room row), as decided by
 * [SyncManager]. This is the per-record signal that [SyncResult] is aggregated from - it is
 * deliberately kept separate from Room's `PENDING_*` state (see [SyncManager.sync]): a row can be
 * `PENDING_*` because it failed in a way worth automatically retrying, or because it failed in a
 * way that automatically repeating the exact same request would never fix.
 */
sealed interface PushResult {

    /** The push succeeded; the row was applied to Room (or is otherwise no longer this pass's concern). */
    data object Success : PushResult

    /** A transient failure - network/IO, timeout, or a 5xx response - worth retrying automatically. */
    data object RetryableFailure : PushResult

    /**
     * A failure that repeating the identical request will not fix: a 4xx business/validation
     * error, or a 401 that [com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator]
     * could not recover (see [AppError.Unauthorized]).
     */
    data object NonRetryableFailure : PushResult
}

/**
 * Classifies an already-categorized [AppError] into the coarser retry decision [SyncManager]
 * needs. Delegates entirely to the existing [AppError]/[com.pahntd.expensetracker.data.remote.error.toAppError]
 * classification rather than re-deriving it from a status code or exception type here.
 */
fun AppError.toPushResult(): PushResult = when (this) {
    is AppError.Network -> PushResult.RetryableFailure
    is AppError.Server -> PushResult.RetryableFailure
    is AppError.Unauthorized -> PushResult.NonRetryableFailure
    is AppError.Client -> PushResult.NonRetryableFailure
    is AppError.Unknown -> PushResult.NonRetryableFailure
}
