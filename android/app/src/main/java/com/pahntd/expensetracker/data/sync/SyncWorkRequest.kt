package com.pahntd.expensetracker.data.sync

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder

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
        .build()
}
