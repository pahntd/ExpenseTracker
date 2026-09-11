package com.pahntd.expensetracker.data.remote.interceptor

import com.pahntd.expensetracker.data.auth.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches `Authorization: Bearer <accessToken>` to every request, using the in-memory access
 * token cached by [SessionManager]. If there is no access token, the request proceeds unchanged.
 *
 * `intercept()` runs synchronously on the calling thread, so it reads
 * [SessionManager.getCurrentAccessToken] rather than DataStore directly.
 */
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val accessToken = sessionManager.getCurrentAccessToken()
        val request = chain.request().let { original ->
            if (accessToken.isNullOrBlank()) {
                original
            } else {
                original.newBuilder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
            }
        }
        return chain.proceed(request)
    }
}
