package com.pahntd.expensetracker.data.sync

import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import com.pahntd.expensetracker.data.remote.api.CategoryApi
import com.pahntd.expensetracker.data.remote.api.TransactionApi
import javax.inject.Inject

/**
 * Owns sync orchestration between Room and the remote API. Push/pull/merge logic is added in
 * later parts; this is currently a skeleton establishing the dependency structure.
 */
class SyncManager @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val categoryApi: CategoryApi,
    private val transactionApi: TransactionApi
) {

    suspend fun sync(trigger: SyncTrigger) {
        // Push/pull/merge orchestration is implemented in a later part.
    }
}
