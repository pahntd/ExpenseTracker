package com.pahntd.expensetracker.data.sync

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import javax.inject.Inject

/**
 * Single entry point for requesting a sync pass. Every trigger source (login, app startup,
 * manual refresh, reconnect, periodic, ...) calls [enqueueSync] instead of talking to
 * [WorkManager] directly, so all of them converge on the same [UNIQUE_SYNC_WORK_NAME] unique
 * work instead of each spinning up its own concurrent [SyncWorker] run.
 */
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager
) {

    /**
     * Requests a sync pass for [trigger]. [ExistingWorkPolicy.KEEP] means this is a no-op
     * whenever unfinished work already sits under [UNIQUE_SYNC_WORK_NAME] - it does not queue a
     * second run behind it. Once that existing run finishes, WorkManager itself drops it from
     * the unique-work slot, so the next call here enqueues a fresh one; no "is sync running"
     * state is tracked here or anywhere else.
     */
    fun enqueueSync(trigger: SyncTrigger) {
        workManager.enqueueUniqueWork(
            UNIQUE_SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            syncWorkRequest(trigger)
        )
    }

    /**
     * Registers the recurring [PeriodicSyncTriggerWorker] as unique periodic work - a safety net
     * on top of the event-driven triggers, not a second sync pipeline; each time it fires it just
     * calls [enqueueSync] with [SyncTrigger.PERIODIC], same as any other trigger source.
     *
     * [ExistingPeriodicWorkPolicy.KEEP]: this is meant to be called from app-process
     * initialization (see [com.pahntd.expensetracker.ExpenseApplication]), which can run many
     * times a day as the process is killed and restarted. `KEEP` makes repeated calls a no-op once
     * the schedule already exists, instead of restarting the interval's clock (or dropping a
     * currently-pending run) on every single process start - the periodic schedule is app-level
     * configuration, not a per-launch event like [enqueueSync]'s triggers are.
     */
    /**
     * Cancels the unique one-time sync work enqueued by [enqueueSync], if any is currently queued
     * or running. Used by logout so background sync can't keep pushing/pulling for the account
     * being logged out. The periodic safety-net schedule from [schedulePeriodicSync] is left in
     * place - once the session is cleared, requests go out unauthenticated (see
     * [com.pahntd.expensetracker.data.remote.interceptor.AuthInterceptor]), so a later periodic
     * run has no account to read or write against.
     */
    fun cancelSync() {
        workManager.cancelUniqueWork(UNIQUE_SYNC_WORK_NAME)
    }

    fun schedulePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncTriggerWorkRequest()
        )
    }

    companion object {
        const val UNIQUE_SYNC_WORK_NAME = "expense_tracker_sync"
        const val UNIQUE_PERIODIC_SYNC_WORK_NAME = "expense_tracker_periodic_sync"
    }
}
