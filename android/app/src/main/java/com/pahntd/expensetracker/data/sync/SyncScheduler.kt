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
     * Cancels the unique one-time sync work enqueued by [enqueueSync], if any is currently queued
     * or running. Used by logout so background sync can't keep pushing/pulling for the account
     * being logged out.
     */
    fun cancelSync() {
        workManager.cancelUniqueWork(UNIQUE_SYNC_WORK_NAME)
    }

    /**
     * Registers the recurring [PeriodicSyncTriggerWorker] as unique periodic work - a safety net
     * on top of the event-driven triggers, not a second sync pipeline; each time it fires it just
     * calls [enqueueSync] with [SyncTrigger.PERIODIC], same as any other trigger source.
     *
     * [ExistingPeriodicWorkPolicy.KEEP]: this is meant to be called from app-process
     * initialization (see [com.pahntd.expensetracker.ExpenseApplication]) and from a successful
     * login (see [com.pahntd.expensetracker.ui.login.LoginViewModel]) - both of which can run
     * several times per process lifetime (process restarts; logout followed by a different
     * account logging back in). `KEEP` makes repeated calls a no-op once the schedule already
     * exists, instead of restarting the interval's clock (or dropping a currently-pending run) on
     * every single call - the periodic schedule is app-level configuration, not a per-launch event
     * like [enqueueSync]'s triggers are.
     */
    fun schedulePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSyncTriggerWorkRequest()
        )
    }

    /**
     * Cancels the periodic safety net registered by [schedulePeriodicSync]. Used by logout so no
     * account's data is read or written by a periodic run firing after the session is gone.
     * Callers that cancel this must also call [schedulePeriodicSync] again once a new session
     * exists (see [com.pahntd.expensetracker.ui.login.LoginViewModel]) - `KEEP`-registered work
     * only comes back on its own at the next app process start
     * ([com.pahntd.expensetracker.ExpenseApplication]), not on a same-process re-login.
     */
    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_SYNC_WORK_NAME)
    }

    companion object {
        const val UNIQUE_SYNC_WORK_NAME = "expense_tracker_sync"
        const val UNIQUE_PERIODIC_SYNC_WORK_NAME = "expense_tracker_periodic_sync"
    }
}
