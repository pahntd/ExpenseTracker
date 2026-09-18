package com.pahntd.expensetracker.data.sync

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder

/**
 * Builds the one-off [SyncWorker] request, constrained to [NetworkType.CONNECTED] so WorkManager
 * itself holds it pending while offline and runs it once a network is available - [SyncWorker]
 * never has to check connectivity itself. `CONNECTED` only means a network interface is up, not
 * that the backend is reachable; a run that starts and still can't reach the API is handled by
 * the existing [SyncResult]/`Result.retry()` contract, not by this constraint.
 *
 * Enqueuing this request (unique work, trigger-specific scheduling, backoff) is not part of this
 * yet - this only builds the request.
 */
fun syncWorkRequest(): OneTimeWorkRequest {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    return OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(constraints)
        .build()
}
