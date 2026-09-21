package com.pahntd.expensetracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic safety net on top of the event-driven triggers (login/startup/reconnect/manual). Its
 * only responsibility is to request a [SyncTrigger.PERIODIC] pass through the same
 * [SyncScheduler.enqueueSync] entry point every other trigger source uses - it holds no sync
 * logic of its own. That call converges on the same unique `expense_tracker_sync` work as every
 * other trigger, so the actual push work still happens entirely inside [SyncWorker]/[SyncManager].
 */
@HiltWorker
class PeriodicSyncTriggerWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncScheduler: SyncScheduler
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        syncScheduler.enqueueSync(SyncTrigger.PERIODIC)
        return Result.success()
    }
}
