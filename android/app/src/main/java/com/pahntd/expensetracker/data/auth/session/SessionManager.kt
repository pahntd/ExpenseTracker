package com.pahntd.expensetracker.data.auth.session

import androidx.datastore.core.DataStore
import com.pahntd.expensetracker.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    /**
     * In-memory cache of the current access token, kept in sync with [dataStore] in the
     * background. This is what [AuthInterceptor][com.pahntd.expensetracker.data.remote.interceptor.AuthInterceptor]
     * reads: `Interceptor.intercept()` is synchronous and must never touch DataStore or block on
     * a coroutine, so it cannot read [dataStore] directly. DataStore remains the source of truth;
     * this field only mirrors it for synchronous access.
     */
    @Volatile
    private var currentAccessToken: String? = null

    /**
     * In-memory cache of the current refresh token, mirrored the same way as [currentAccessToken].
     * Read by [AuthAuthenticator][com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator],
     * whose `authenticate()` also runs synchronously on an OkHttp thread.
     */
    @Volatile
    private var currentRefreshToken: String? = null

    init {
        dataStore.data
            .onEach { session ->
                currentAccessToken = session.accessToken.ifBlank { null }
                currentRefreshToken = session.refreshToken.ifBlank { null }
            }
            .launchIn(applicationScope)
    }

    /** Synchronous snapshot of the current access token, or `null` if there is none. */
    fun getCurrentAccessToken(): String? = currentAccessToken

    /** Synchronous snapshot of the current refresh token, or `null` if there is none. */
    fun getCurrentRefreshToken(): String? = currentRefreshToken

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
     * Synchronous counterpart to [updateAccessToken], for callers that cannot suspend.
     * [AuthAuthenticator][com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator]
     * runs on an OkHttp thread and must return the rebuilt request immediately, so this updates
     * the in-memory cache right away and persists to DataStore in the background on
     * [applicationScope].
     */
    fun updateAccessTokenBlocking(accessToken: String) {
        currentAccessToken = accessToken
        applicationScope.launch {
            updateAccessToken(accessToken)
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
