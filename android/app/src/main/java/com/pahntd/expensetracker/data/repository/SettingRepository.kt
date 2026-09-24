package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.data.auth.session.SessionManager
import com.pahntd.expensetracker.data.local.database.ExpenseDatabase
import com.pahntd.expensetracker.data.sync.SyncScheduler
import com.pahntd.expensetracker.data.sync.SyncStatusHolder
import javax.inject.Inject

class SettingRepository @Inject constructor(
    private val database: ExpenseDatabase,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val syncScheduler: SyncScheduler,
    private val syncStatusHolder: SyncStatusHolder
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
     * refresh token, wipes the local dataset, clears the session, then resets [syncStatusHolder]
     * to `IDLE` - in that order, so cancelling sync happens before anything it reads (Room, the
     * session) is torn down. Transactions are cleared before categories since a transaction can
     * reference a category id (foreign key). The architecture is single-account, so this always
     * clears the complete local dataset rather than scoping it (or [syncStatusHolder]) to a user
     * id - there is only ever one local account, so a plain reset to `IDLE` is enough; no
     * per-user `SyncState` is needed.
     *
     * The [syncStatusHolder] reset is explicit and unconditional - it does not rely on the
     * just-cancelled sync coroutine to clean up after itself. [SyncManager.sync] already never
     * writes a terminal `SYNCED`/`SYNC_FAILED`/`OFFLINE` once it observes cancellation (its
     * `CancellationException` handler only ever sets `IDLE`, then rethrows), so once cancellation
     * has actually been observed by that coroutine, no stale write can land after this point. The
     * only residual window is between [SyncScheduler.cancelSync] being requested here and that
     * coroutine noticing it at its next suspension point (cooperative cancellation, not
     * instantaneous) - calling `cancelSync()` first, as the very first step, minimizes it as much
     * as this architecture reasonably can without adding new synchronization.
     *
     * Network revocation never gates local cleanup - see [AuthRepository.logout] - and neither
     * does any of this touch [com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator]
     * or token-refresh behavior, which are unrelated to logout.
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

        syncStatusHolder.reset()
    }
}
