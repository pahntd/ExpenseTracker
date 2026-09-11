package com.pahntd.expensetracker.data.remote.api

import com.pahntd.expensetracker.data.remote.dto.RefreshTokenRequest
import com.pahntd.expensetracker.data.remote.dto.RefreshTokenResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Synchronous counterpart to [AuthApi.refresh], used only by
 * [com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator]. OkHttp's
 * `Authenticator.authenticate()` runs synchronously on an OkHttp dispatcher thread and must not
 * suspend, so this returns a blocking [Call] instead of a `suspend` function.
 */
interface TokenRefreshApi {

    @POST("auth/refresh")
    fun refresh(
        @Body request: RefreshTokenRequest
    ): Call<RefreshTokenResponse>
}
