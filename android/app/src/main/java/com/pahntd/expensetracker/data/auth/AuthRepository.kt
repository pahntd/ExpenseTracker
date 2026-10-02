package com.pahntd.expensetracker.data.auth

import com.pahntd.expensetracker.data.remote.dto.RegisterRequest
import com.pahntd.expensetracker.data.remote.api.AuthApi
import com.pahntd.expensetracker.data.remote.dto.LoginRequest
import com.pahntd.expensetracker.data.remote.dto.RefreshTokenRequest
import com.pahntd.expensetracker.data.remote.error.AppError
import com.pahntd.expensetracker.data.remote.error.HTTP_TOO_MANY_REQUESTS
import com.pahntd.expensetracker.data.remote.error.RefreshRateLimitedException
import com.pahntd.expensetracker.data.remote.error.retryAfterSeconds
import com.pahntd.expensetracker.data.remote.error.toAppError
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.CancellationException
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val refreshCooldown: RefreshCooldown,
) {

    suspend fun login(email: String, password: String): LoginResult {
        return try {
            val response = authApi.login(
                LoginRequest(email = email, password = password)
            )
            LoginResult.Success(response)
        } catch (e: HttpException) {
            // The running Ktor backend maps invalid credentials to 400 (IllegalArgumentException
            // -> StatusPages). 401 is also accepted here in case the contract tightens later.
            if (e.code() == 400 || e.code() == 401) {
                LoginResult.InvalidCredentials
            } else if (e.code() == HTTP_TOO_MANY_REQUESTS) {
                LoginResult.RateLimited(e.retryAfterSeconds())
            } else {
                LoginResult.UnknownError
            }
        } catch (e: IOException) {
            LoginResult.NetworkError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoginResult.UnknownError
        }
    }

    suspend fun register(email: String, password: String): RegisterResult {
        return try {
            val response = authApi.register(
                RegisterRequest(email = email, password = password)
            )
            RegisterResult.Success(response)
        } catch (e: HttpException) {
            // The running Ktor backend maps "Email already exists" to 400 (IllegalArgumentException
            // -> StatusPages). 409 is also accepted here in case the contract tightens later.
            if (e.code() == 400 || e.code() == 409) {
                RegisterResult.EmailAlreadyExists
            } else if (e.code() == HTTP_TOO_MANY_REQUESTS) {
                RegisterResult.RateLimited(e.retryAfterSeconds())
            } else {
                RegisterResult.UnknownError
            }
        } catch (e: IOException) {
            RegisterResult.NetworkError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RegisterResult.UnknownError
        }
    }

    /**
     * POST /auth/refresh. While [refreshCooldown] is running (an earlier 429's Retry-After, from
     * here or from the authenticator) the endpoint is not called at all.
     */
    suspend fun refresh(refreshToken: String): RefreshResult {
        refreshCooldown.remainingSeconds()?.let { return RefreshResult.RateLimited(it) }
        return try {
            val response = authApi.refresh(
                RefreshTokenRequest(refreshToken = refreshToken)
            )
            RefreshResult.Success(response)
        } catch (e: HttpException) {
            // The Ktor backend answers 401 for an invalid / revoked / expired refresh token.
            // A 400 (malformed body) means the token is unusable too.
            if (e.code() == 401 || e.code() == 400) {
                RefreshResult.InvalidRefreshToken
            } else if (e.code() == HTTP_TOO_MANY_REQUESTS) {
                // Temporary, not a verdict on the token: record the cooldown, never retry here.
                val retryAfterSeconds = e.retryAfterSeconds()
                refreshCooldown.start(retryAfterSeconds)
                RefreshResult.RateLimited(retryAfterSeconds)
            } else {
                RefreshResult.UnknownError
            }
        } catch (e: RefreshRateLimitedException) {
            // Thrown by AuthAuthenticator (it already recorded any cooldown) - must be caught
            // before IOException, which it extends.
            RefreshResult.RateLimited(e.retryAfterSeconds)
        } catch (e: IOException) {
            RefreshResult.NetworkError
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RefreshResult.UnknownError
        }
    }

    /**
     * Best-effort POST /auth/logout to revoke [refreshToken] server-side. Every outcome -
     * success, a rejected/already-invalid token, or a network failure - is swallowed here: local
     * logout must never depend on this call reaching the server or succeeding.
     */
    suspend fun logout(refreshToken: String) {
        try {
            authApi.logout(RefreshTokenRequest(refreshToken = refreshToken))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort - see kdoc above.
        }
    }

    /**
     * DELETE /account for the currently authenticated user. It goes through the regular
     * authenticated client, so an expired access token is refreshed and the request retried by
     * [com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator] like any other call;
     * only a 401 it could not recover surfaces as [DeleteAccountResult.SessionExpired].
     *
     * Only a 2xx is [DeleteAccountResult.Success]. A network failure never counts as success: the
     * request may or may not have reached the server, so the caller must keep local data.
     */
    suspend fun deleteAccount(): DeleteAccountResult {
        return try {
            val response = authApi.deleteAccount()
            if (response.isSuccessful) {
                DeleteAccountResult.Success
            } else {
                response.toAppError().toDeleteAccountResult()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toAppError().toDeleteAccountResult()
        }
    }

    private fun AppError.toDeleteAccountResult(): DeleteAccountResult = when (this) {
        AppError.Network -> DeleteAccountResult.NetworkError
        AppError.Unauthorized -> DeleteAccountResult.SessionExpired
        is AppError.Client ->
            if (code == 404) DeleteAccountResult.AccountNotFound else DeleteAccountResult.UnknownError
        is AppError.Server -> DeleteAccountResult.ServerError
        // Rate limited (the DELETE itself, or the refresh it needed): nothing was deleted and the
        // session is intact; the generic "try again" outcome keeps local data, like any failure.
        is AppError.RateLimited -> DeleteAccountResult.UnknownError
        is AppError.Unknown -> DeleteAccountResult.UnknownError
    }
}
