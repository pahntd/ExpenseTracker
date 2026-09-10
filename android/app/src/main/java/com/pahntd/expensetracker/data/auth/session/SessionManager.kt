package com.pahntd.expensetracker.data.auth.session

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single application-layer entry point for the locally persisted authentication [Session].
 *
 * Everything above this (ViewModels, Fragments) talks to [SessionManager]; nothing else touches
 * `DataStore<Session>`. Encryption at rest is already handled by [SessionSerializer], so this
 * class only reads and writes plain [Session] objects.
 *
 * Scope note: this is local persistence only. It does not call the network, refresh or validate
 * tokens, or perform login/logout — that lives in later steps.
 */
@Singleton
class SessionManager @Inject constructor(
    private val dataStore: DataStore<Session>,
) {

    /**
     * Persists the session returned by a successful authentication. Overwrites any existing
     * session. DataStore performs its own I/O, so this just suspends until the write completes.
     */
    suspend fun saveSession(
        userId: String,
        accessToken: String,
        refreshToken: String,
    ) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setUserId(userId)
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .build()
        }
    }

    /**
     * Replaces only the access token, leaving the refresh token and user id untouched. Used after
     * a successful /auth/refresh (the backend does not rotate refresh tokens).
     */
    suspend fun updateAccessToken(accessToken: String) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setAccessToken(accessToken)
                .build()
        }
    }

    /**
     * Emits the current [Session] whenever it changes, or `null` when there is no active local
     * session. "Active" is decided purely locally: a non-blank refresh token means a session
     * exists; a blank one means it does not. No server call or token validation happens here.
     */
    fun observeSession(): Flow<Session?> =
        dataStore.data.map { session ->
            if (session.refreshToken.isBlank()) null else session
        }

    /**
     * Clears the local session by resetting the store to [Session.getDefaultInstance]. The
     * DataStore file and the Tink keyset are left in place.
     */
    suspend fun clearSession() {
        dataStore.updateData { Session.getDefaultInstance() }
    }
}
