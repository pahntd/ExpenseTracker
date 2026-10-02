package com.pahntd.expensetracker.data.remote.authenticator

import com.pahntd.expensetracker.data.auth.RefreshCooldown
import com.pahntd.expensetracker.data.auth.session.SessionManager
import com.pahntd.expensetracker.data.remote.api.TokenRefreshApi
import com.pahntd.expensetracker.data.remote.dto.RefreshTokenRequest
import com.pahntd.expensetracker.data.remote.error.HTTP_TOO_MANY_REQUESTS
import com.pahntd.expensetracker.data.remote.error.RefreshRateLimitedException
import com.pahntd.expensetracker.data.remote.error.retryAfterSeconds
import com.pahntd.expensetracker.di.BareClient
import com.pahntd.expensetracker.utils.AccountPreferencesCleaner
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject

/**
 * Refreshes the access token when a request comes back with a 401, then retries it once with the
 * new token.
 *
 * `authenticate()` runs synchronously on an OkHttp dispatcher thread, so it never touches
 * DataStore directly and never uses `runBlocking`: it reads [SessionManager]'s in-memory refresh
 * token, calls `/auth/refresh` through [tokenRefreshApi] (a [BareClient] Retrofit service, backed
 * by an [okhttp3.OkHttpClient] with no [com.pahntd.expensetracker.data.remote.interceptor.AuthInterceptor]
 * or [AuthAuthenticator], so refreshing can never itself trigger another 401 -> refresh cycle),
 * and updates [SessionManager] through its synchronous update method.
 *
 * The backend revokes refresh tokens rather than rotating them, so only the access token is
 * replaced; the refresh token and user id are left untouched.
 *
 * Single retry: [Response.priorResponse] is non-null once a request has already been retried
 * through this authenticator, so a second 401 on the same call chain returns `null` immediately
 * instead of refreshing again.
 *
 * Concurrent 401s: OkHttp calls `authenticate()` from whichever dispatcher thread hit the 401, so
 * several requests can arrive here at once. The whole method body is synchronized on this
 * (singleton) instance, and each caller re-checks the in-memory access token against the one its
 * own failed request used: if another thread already refreshed while this one was waiting on the
 * lock, the token will have moved on, and this thread just retries with it instead of calling
 * `/auth/refresh` again.
 *
 * Rate limiting: a 429 from `/auth/refresh` is temporary and says nothing about the refresh
 * token, so the session is kept. The original request is not replayed with its stale token;
 * instead [RefreshRateLimitedException] is thrown, failing that call with a typed rate-limit
 * error. The `Retry-After` is recorded in [refreshCooldown], and until it elapses every further
 * 401 fails the same way without calling `/auth/refresh` at all. Requests that were already
 * waiting on the lock when the 429 arrived also fail without a second refresh, even if the
 * backend sent no `Retry-After`. Nothing here retries, and nothing here shows UI.
 */
class AuthAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    @BareClient private val tokenRefreshApi: TokenRefreshApi,
    private val accountPreferencesCleaner: AccountPreferencesCleaner,
    private val refreshCooldown: RefreshCooldown,
) : Authenticator {

    /** Number of `/auth/refresh` responses received so far. Written under the lock. */
    @Volatile
    private var refreshResponseCount = 0L

    /** Whether the most recent `/auth/refresh` response was a 429. Guarded by the lock. */
    private var lastRefreshRateLimited = false

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null

        val failedAuthHeader = response.request.header("Authorization")
        val refreshResponsesSeen = refreshResponseCount

        synchronized(this) {
            val cachedAuthHeader = sessionManager.getCurrentAccessToken()?.let { "Bearer $it" }

            // Another thread already refreshed while we were waiting for the lock.
            if (cachedAuthHeader != null && cachedAuthHeader != failedAuthHeader) {
                return response.request.newBuilder()
                    .header("Authorization", cachedAuthHeader)
                    .build()
            }

            val refreshToken = sessionManager.getCurrentRefreshToken()
            if (refreshToken == null) {
                sessionManager.clearSessionBlocking()
                accountPreferencesCleaner.clear()
                return null
            }

            // Still inside a Retry-After window from an earlier 429: don't call /auth/refresh.
            refreshCooldown.remainingSeconds()?.let { throw RefreshRateLimitedException(it) }

            // Another thread's refresh came back 429 while we were waiting for the lock (with no
            // usable Retry-After, so no cooldown): share its outcome instead of refreshing again.
            if (refreshResponseCount != refreshResponsesSeen && lastRefreshRateLimited) {
                throw RefreshRateLimitedException(retryAfterSeconds = null)
            }

            val refreshResponse = try {
                tokenRefreshApi.refresh(
                    RefreshTokenRequest(refreshToken = refreshToken)
                ).execute()
            } catch (e: IOException) {
                // Network failure/timeout while refreshing is not a definitive auth failure -
                // leave the session intact so a later attempt (once connectivity returns) can
                // still succeed, instead of clearing it like an actually-rejected refresh token.
                return null
            }

            refreshResponseCount++
            lastRefreshRateLimited = refreshResponse.code() == HTTP_TOO_MANY_REQUESTS

            if (lastRefreshRateLimited) {
                // Rate limited, not rejected: keep the session, start the cooldown, and fail the
                // original call with a typed error rather than replaying it with the old token.
                val retryAfterSeconds = refreshResponse.retryAfterSeconds()
                refreshCooldown.start(retryAfterSeconds)
                throw RefreshRateLimitedException(retryAfterSeconds)
            }

            val newAccessToken = if (refreshResponse.isSuccessful) {
                refreshResponse.body()?.accessToken
            } else {
                null
            }

            if (newAccessToken.isNullOrBlank()) {
                sessionManager.clearSessionBlocking()
                accountPreferencesCleaner.clear()
                return null
            }

            sessionManager.updateAccessTokenBlocking(newAccessToken)

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }
    }
}
