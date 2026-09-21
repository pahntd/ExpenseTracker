package com.pahntd.expensetracker.data.sync

import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.remote.error.AppError

/**
 * Outcome of one pull pass ([PullManager.pull]): a complete server snapshot for categories and
 * transactions, or why one couldn't be produced. Deliberately all-or-nothing - there is no
 * partial-success variant - so a caller can never merge or persist half a snapshot.
 */
sealed interface PullResult {

    /** Both resources were fetched and every item mapped cleanly. */
    data class Success(
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>
    ) : PullResult

    /** A transient failure - network/IO, timeout, or a 5xx response - worth retrying automatically. */
    data object RetryableFailure : PullResult

    /**
     * A failure that repeating the identical request will not fix: a 4xx business/validation
     * error, a 401 that [com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator]
     * could not recover, or a successful response containing at least one item that can't be
     * safely mapped to a local entity.
     */
    data object NonRetryableFailure : PullResult
}

/**
 * Classifies an already-categorized [AppError] into the coarser retry decision [PullManager]
 * needs. Delegates entirely to the existing [AppError] classification, mirroring
 * [toPushResult] on the pull side.
 */
fun AppError.toPullResult(): PullResult = when (this) {
    is AppError.Network -> PullResult.RetryableFailure
    is AppError.Server -> PullResult.RetryableFailure
    is AppError.Unauthorized -> PullResult.NonRetryableFailure
    is AppError.Client -> PullResult.NonRetryableFailure
    is AppError.Unknown -> PullResult.NonRetryableFailure
}
