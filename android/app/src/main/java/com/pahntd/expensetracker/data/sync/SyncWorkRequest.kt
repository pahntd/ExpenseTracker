package com.pahntd.expensetracker.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

/**
 * Initial delay before WorkManager's first retry of a `Result.retry()`'d [SyncWorker] run, then
 * doubled ([BackoffPolicy.EXPONENTIAL]) on each subsequent retry of the same [SyncScheduler.UNIQUE_SYNC_WORK_NAME]
 * unique work. Retry timing is entirely WorkManager's responsibility from here - [SyncManager]/
 * [SyncWorker] never sleep, delay, or loop themselves.
 */
private const val SYNC_BACKOFF_DELAY_SECONDS = 30L

/**
 * How often WorkManager wakes [PeriodicSyncTriggerWorker] up. Periodic sync is only a safety net
 * on top of the event-driven triggers (login/startup/reconnect/manual), so the exact cadence isn't
 * critical - kept as its own named constant so it's easy to change while testing. 15 minutes is
 * WorkManager's supported minimum periodic interval; anything shorter is silently clamped up to
 * it by the framework, so there is no way (and no need) to go lower.
 */
private const val PERIODIC_SYNC_INTERVAL_MINUTES = 15L

/**
 * Builds the one-off [SyncWorker] request for [trigger], constrained to [NetworkType.CONNECTED]
 * so WorkManager itself holds it pending while offline and runs it once a network is available -
 * [SyncWorker] never has to check connectivity itself. `CONNECTED` only means a network interface
 * is up, not that the backend is reachable; a run that starts and still can't reach the API is
 * handled by the existing [SyncResult]/`Result.retry()` contract, not by this constraint.
 *
 * [trigger] is passed through `inputData` as control metadata only (the reason this pass was
 * requested) - never entity/pending payload data, which stays in Room as the source of truth for
 * [SyncManager] to read.
 *
 * Enqueuing this request as unique work is [SyncScheduler]'s job, not this function's.
 */
fun syncWorkRequest(trigger: SyncTrigger): OneTimeWorkRequest {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val inputData = Data.Builder()
        .putString(SyncWorker.KEY_TRIGGER, trigger.name)
        .build()

    return OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .setInputData(inputData)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, SYNC_BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .build()
}

/**
 * Builds the recurring [PeriodicSyncTriggerWorker] request. Deliberately carries no constraints
 * of its own: [PeriodicSyncTriggerWorker] does no network I/O, it only calls
 * [SyncScheduler.enqueueSync] - a local WorkManager call - so gating *that* on connectivity would
 * just duplicate the one real constraint [syncWorkRequest] already applies to the actual sync
 * work. Network gating stays owned by exactly one place.
 *
 * Enqueuing this request as unique periodic work is [SyncScheduler]'s job, not this function's.
 */
fun periodicSyncTriggerWorkRequest(): PeriodicWorkRequest {
    return PeriodicWorkRequestBuilder<PeriodicSyncTriggerWorker>(PERIODIC_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
        .build()
}
