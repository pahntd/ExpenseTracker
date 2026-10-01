package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.data.auth.DeleteAccountResult
import com.pahntd.expensetracker.data.auth.session.SessionManager
import com.pahntd.expensetracker.data.local.database.ExpenseDatabase
import com.pahntd.expensetracker.utils.AccountPreferencesCleaner
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingRepository @Inject constructor(
    private val database: ExpenseDatabase,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val localAccountDataCleaner: LocalAccountDataCleaner,
    private val accountPreferencesCleaner: AccountPreferencesCleaner
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
     * Logs the current local account out and leaves the device in a clean logged-out state:
     * cancels background sync and wipes the local dataset ([LocalAccountDataCleaner.wipe] - sync
     * is cancelled before anything it reads is torn down), best-effort revokes the refresh token,
     * clears the account-scoped preferences ([AccountPreferencesCleaner], which also revokes every
     * feature unlock), then clears the session and forgets the last user id
     * ([SessionManager.clearSessionAndForgetUser]) - Room is already empty, so the next login has
     * nothing to compare against and is treated as a new account.
     *
     * The sync status reset inside [LocalAccountDataCleaner.wipe] is explicit and unconditional -
     * it does not rely on the just-cancelled sync coroutine to clean up after itself.
     * [SyncManager.sync] already never writes a terminal `SYNCED`/`SYNC_FAILED`/`OFFLINE` once it
     * observes cancellation (its `CancellationException` handler only ever sets `IDLE`, then
     * rethrows), so once cancellation has actually been observed by that coroutine, no stale write
     * can land after this point. The only residual window is between
     * [com.pahntd.expensetracker.data.sync.SyncScheduler.cancelSync] being requested and that
     * coroutine noticing it at its next suspension point (cooperative cancellation, not
     * instantaneous) - cancelling sync as the very first step minimizes it as much as this
     * architecture reasonably can without adding new synchronization.
     *
     * The refresh token is revoked before the session is cleared (the call needs it). Network
     * revocation never gates local cleanup - see [AuthRepository.logout] - and neither does any
     * of this touch [com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator] or
     * token-refresh behavior, which are unrelated to logout.
     */
    suspend fun logout() {
        localAccountDataCleaner.wipe()

        sessionManager.getCurrentRefreshToken()?.let { refreshToken ->
            authRepository.logout(refreshToken)
        }

        clearAccountPreferencesAndForgetUser()
    }

    /**
     * Permanently deletes the signed-in account server-side (DELETE /account), then - only once
     * the server has confirmed it - leaves the device in the same clean state as [logout]: Room
     * wiped and sync cancelled ([LocalAccountDataCleaner.wipe]), account-scoped preferences
     * cleared, and the session cleared with the last user id forgotten, so login's same-user
     * shortcut can never hand the deleted account's rows to anyone.
     *
     * Any non-success result returns without touching local state. Unlike [logout] there is no
     * refresh-token revocation: the backend already deleted every refresh token of the account.
     *
     * Cleanup runs [NonCancellable]: once the server has deleted the account, leaving the screen
     * (and cancelling the caller's scope) must not strand a half-cleared local session.
     */
    suspend fun deleteAccount(): DeleteAccountResult {
        val result = authRepository.deleteAccount()
        if (result == DeleteAccountResult.Success) {
            withContext(NonCancellable) {
                localAccountDataCleaner.wipe()
                clearAccountPreferencesAndForgetUser()
            }
        }
        return result
    }

    /** Shared tail of [logout] and [deleteAccount]; Room must already be wiped. */
    private suspend fun clearAccountPreferencesAndForgetUser() {
        accountPreferencesCleaner.clear()
        sessionManager.clearSessionAndForgetUser()
    }
}
