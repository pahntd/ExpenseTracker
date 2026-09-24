package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.local.database.ExpenseDatabase
import javax.inject.Inject

class SettingRepository @Inject constructor(
    private val database: ExpenseDatabase
) {

    /**
     * Whether any category or transaction has a local change not yet pushed to the server
     * (`PENDING_CREATE`/`PENDING_UPDATE`/`PENDING_DELETE`). Short-circuits on the first hit so a
     * transaction check is skipped once categories already answer this.
     */
    suspend fun hasPendingChanges(): Boolean {
        return database.categoryDao().hasPendingChanges() ||
            database.transactionDao().hasPendingChanges()
    }
}