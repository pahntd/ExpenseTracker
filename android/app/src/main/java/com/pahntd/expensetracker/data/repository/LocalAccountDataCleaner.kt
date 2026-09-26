package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.local.database.ExpenseDatabase
import com.pahntd.expensetracker.data.sync.SyncScheduler
import com.pahntd.expensetracker.data.sync.SyncStatusHolder
import javax.inject.Inject

/**
 * Wipes the local account dataset (Room) - shared by manual logout ([SettingRepository.logout])
 * and by login when the new account differs from the last one on this device
 * ([com.pahntd.expensetracker.ui.login.LoginViewModel]).
 *
 * Order matters: background sync (the one-time work and the periodic safety net) is cancelled
 * first, so no sync pass keeps reading or pushing rows while they are deleted. Transactions are
 * cleared before categories since a transaction can reference a category id (foreign key). The
 * architecture is single-account, so this always clears the complete local dataset rather than
 * scoping it to a user id, and [syncStatusHolder] goes back to `IDLE` unconditionally - see
 * [SettingRepository.logout] for why that reset does not rely on the cancelled sync.
 *
 * Callers that cancel sync here must re-arm it once a new session exists
 * ([SyncScheduler.schedulePeriodicSync]), as login already does.
 */
class LocalAccountDataCleaner @Inject constructor(
    private val database: ExpenseDatabase,
    private val syncScheduler: SyncScheduler,
    private val syncStatusHolder: SyncStatusHolder
) {

    suspend fun wipe() {
        syncScheduler.cancelSync()
        syncScheduler.cancelPeriodicSync()

        database.transactionDao().deleteAll()
        database.categoryDao().deleteAll()

        syncStatusHolder.reset()
    }
}
