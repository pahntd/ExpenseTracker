package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.data.auth.DeleteAccountResult
import com.pahntd.expensetracker.data.auth.RefreshResult
import com.pahntd.expensetracker.data.auth.session.SessionManager
import com.pahntd.expensetracker.data.local.database.ExpenseDatabase
import com.pahntd.expensetracker.data.sync.SyncScheduler
import com.pahntd.expensetracker.utils.AccountPreferencesCleaner
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingRepository @Inject constructor(
    private val database: ExpenseDatabase,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val localAccountDataCleaner: LocalAccountDataCleaner,
    private val accountPreferencesCleaner: AccountPreferencesCleaner,
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
     * Background sync is paused and cancelled *before* the request
     * ([SyncScheduler.pauseForAccountDeletion]), so no pass pushes or pulls for this account while
     * it is being deleted and no new one can be scheduled. The `finally` always lifts the pause:
     * re-arming sync when the account still exists (any failure, or the caller being cancelled
     * mid-request), and leaving it un-armed after a deletion - there is no session left, and the
     * next login re-arms it.
     *
     * Ambiguous answers are resolved through the existing auth endpoints rather than guessed (see
     * [resolve]). Every remaining failure leaves Room untouched. Unlike [logout] there is no
     * refresh-token revocation: the backend already deleted every refresh token of the account.
     *
     * Cleanup runs [NonCancellable]: once the server has deleted the account, leaving the screen
     * (and cancelling the caller's scope) must not strand a half-cleared local session.
     */
    suspend fun deleteAccount(): DeleteAccountResult {
        syncScheduler.pauseForAccountDeletion()
        var accountDeleted = false
        try {
            val result = resolve(authRepository.deleteAccount())
            when (result) {
                DeleteAccountResult.Success -> {
                    accountDeleted = true
                    withContext(NonCancellable) {
                        localAccountDataCleaner.wipe()
                        clearAccountPreferencesAndForgetUser()
                    }
                }
                // AuthAuthenticator already ended the session (in memory, persisting in the
                // background) and cleared account prefs - the same forced logout as anywhere else.
                // Persist it before the caller navigates, so Splash reliably sees no session.
                // Room is kept, as on every forced logout: a 401 alone never proves deletion.
                DeleteAccountResult.SessionExpired -> sessionManager.clearSessionAndRememberUser()
                else -> Unit
            }
            return result
        } finally {
            syncScheduler.resumeAfterAccountDeletion(rearm = !accountDeleted)
        }
    }

    /**
     * Narrows the two DELETE /account outcomes whose meaning depends on more than the status code:
     *
     * - [DeleteAccountResult.AccountNotFound] (404): the access token was accepted but the backend
     *   has no such account - the expected answer when retrying after an earlier DELETE whose
     *   response was lost. A 404 alone could also be a missing route, so it is confirmed through
     *   the existing refresh endpoint: the backend deletes every refresh token with the account,
     *   so a definitively rejected refresh token plus this 404 means the account is gone ->
     *   [DeleteAccountResult.Success]. A refresh that still works means the account exists.
     * - [DeleteAccountResult.SessionExpired] (unrecoverable 401): only a real session end if
     *   [com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator] actually cleared
     *   the tokens. It deliberately keeps them when the refresh call itself hit a network error,
     *   and that is reported as [DeleteAccountResult.NetworkError] instead.
     */
    private suspend fun resolve(result: DeleteAccountResult): DeleteAccountResult = when (result) {
        DeleteAccountResult.AccountNotFound -> {
            val refreshToken = sessionManager.getCurrentRefreshToken()
            if (refreshToken == null) {
                DeleteAccountResult.SessionExpired
            } else {
                when (authRepository.refresh(refreshToken)) {
                    RefreshResult.InvalidRefreshToken -> DeleteAccountResult.Success
                    RefreshResult.NetworkError -> DeleteAccountResult.NetworkError
                    is RefreshResult.Success,
                    RefreshResult.UnknownError -> DeleteAccountResult.UnknownError
                }
            }
        }

        DeleteAccountResult.SessionExpired ->
            if (sessionManager.getCurrentRefreshToken() == null) {
                DeleteAccountResult.SessionExpired
            } else {
                DeleteAccountResult.NetworkError
            }

        else -> result
    }

    /** Shared tail of [logout] and [deleteAccount]; Room must already be wiped. */
    private suspend fun clearAccountPreferencesAndForgetUser() {
        accountPreferencesCleaner.clear()
        sessionManager.clearSessionAndForgetUser()
    }
}
