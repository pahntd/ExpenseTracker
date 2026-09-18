package com.pahntd.expensetracker.data.sync

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

    companion object {
        const val UNIQUE_SYNC_WORK_NAME = "expense_tracker_sync"
    }
}
