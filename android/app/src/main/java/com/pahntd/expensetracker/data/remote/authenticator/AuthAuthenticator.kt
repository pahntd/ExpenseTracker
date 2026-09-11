package com.pahntd.expensetracker.data.remote.authenticator

import com.pahntd.expensetracker.data.auth.session.SessionManager
import com.pahntd.expensetracker.data.remote.api.TokenRefreshApi
import com.pahntd.expensetracker.data.remote.dto.RefreshTokenRequest
import com.pahntd.expensetracker.di.BareClient
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
 */
class AuthAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    @BareClient private val tokenRefreshApi: TokenRefreshApi,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null

        val failedAuthHeader = response.request.header("Authorization")

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
                return null
            }

            val newAccessToken = try {
                val refreshResponse = tokenRefreshApi.refresh(
                    RefreshTokenRequest(refreshToken = refreshToken)
                ).execute()
                if (refreshResponse.isSuccessful) {
                    refreshResponse.body()?.accessToken
                } else {
                    null
                }
            } catch (e: IOException) {
                null
            }

            if (newAccessToken.isNullOrBlank()) {
                sessionManager.clearSessionBlocking()
                return null
            }

            sessionManager.updateAccessTokenBlocking(newAccessToken)

            return response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }
    }
}
