package com.pahntd.expensetracker.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Thin WorkManager entry point for sync: it owns no sync logic itself, only delegates to
 * [SyncManager] and maps the resulting [SyncResult] onto a WorkManager [Result]. It never touches
 * Room or the APIs directly, and it never receives per-record data through [WorkerParameters] -
 * the source of truth for what needs syncing is Room's current `PENDING_*` rows, read by
 * [SyncManager] itself, so a re-run after process death picks up the latest state instead of a
 * stale payload.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val trigger = inputData.getString(KEY_TRIGGER)
            ?.let { name -> runCatching { SyncTrigger.valueOf(name) }.getOrNull() }
            ?: SyncTrigger.MANUAL

        return when (syncManager.sync(trigger)) {
            SyncResult.Success -> Result.success()
            SyncResult.Retry -> Result.retry()
        }
    }

    companion object {
        /**
         * Only the [SyncTrigger] enum name is ever passed through `inputData` - a control
         * parameter for which sync pass this is, not the transaction/category payload being
         * synced (that stays in Room, see class doc).
         */
        const val KEY_TRIGGER = "sync_trigger"
    }
}
