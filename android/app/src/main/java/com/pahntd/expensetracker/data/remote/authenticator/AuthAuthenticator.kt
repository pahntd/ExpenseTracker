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
 */
class AuthAuthenticator @Inject constructor(
    private val sessionManager: SessionManager,
    @BareClient private val tokenRefreshApi: TokenRefreshApi,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val refreshToken = sessionManager.getCurrentRefreshToken() ?: return null

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
        } ?: return null

        sessionManager.updateAccessTokenBlocking(newAccessToken)

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .build()
    }
}
