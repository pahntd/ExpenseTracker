package com.pahntd.expensetracker.data.sync

/**
 * Outcome of a [SyncManager.sync] pass, for [SyncWorker] to map onto a WorkManager `Result`.
 * This is a pass-level signal, not a per-record one: [Retry] means at least one row is still
 * `PENDING_*` after the pass (a retryable failure, a skipped FK dependency, ...), not that the
 * whole pass failed - rows that did succeed stay `SYNCED`/deleted and are not retried.
 */
sealed interface SyncResult {
    data object Success : SyncResult
    data object Retry : SyncResult
}
