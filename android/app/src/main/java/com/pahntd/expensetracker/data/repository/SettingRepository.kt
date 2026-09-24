package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.data.auth.session.SessionManager
import com.pahntd.expensetracker.data.local.database.ExpenseDatabase
import com.pahntd.expensetracker.data.sync.SyncScheduler
import javax.inject.Inject

class SettingRepository @Inject constructor(
    private val database: ExpenseDatabase,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val syncScheduler: SyncScheduler
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

    /**
     * Logs the current local account out: cancels background sync (both the one-time work and the
     * periodic safety net - see [SyncScheduler.cancelPeriodicSync]), best-effort revokes the
     * refresh token, wipes the local dataset, then clears the session - in that order, so
     * cancelling sync happens before anything it reads (Room, the session) is torn down.
     * Transactions are cleared before categories since a transaction can reference a category id
     * (foreign key). The architecture is single-account, so this always clears the complete local
     * dataset rather than scoping it to a user id.
     *
     * Network revocation never gates local cleanup - see [AuthRepository.logout].
     */
    suspend fun logout() {
        syncScheduler.cancelSync()
        syncScheduler.cancelPeriodicSync()

        sessionManager.getCurrentRefreshToken()?.let { refreshToken ->
            authRepository.logout(refreshToken)
        }

        database.transactionDao().deleteAll()
        database.categoryDao().deleteAll()

        sessionManager.clearSession()
    }
}
