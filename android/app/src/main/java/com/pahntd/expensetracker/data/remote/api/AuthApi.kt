package com.pahntd.expensetracker.data.remote.api

import com.pahntd.expensetracker.data.remote.dto.RegisterRequest
import com.pahntd.expensetracker.data.remote.dto.RegisterResponse
import com.pahntd.expensetracker.data.remote.dto.LoginRequest
import com.pahntd.expensetracker.data.remote.dto.LoginResponse
import com.pahntd.expensetracker.data.remote.dto.RefreshTokenRequest
import com.pahntd.expensetracker.data.remote.dto.RefreshTokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST

interface AuthApi {

    @POST("login")
    suspend fun login(
        @Body request: LoginRequest
    ): LoginResponse

    @POST("register")
    suspend fun register(
        @Body request: RegisterRequest
    ): RegisterResponse

    @POST("auth/refresh")
    suspend fun refresh(
        @Body request: RefreshTokenRequest
    ): RefreshTokenResponse

    /** Revokes [request]'s refresh token server-side. Called best-effort on logout. */
    @POST("auth/logout")
    suspend fun logout(
        @Body request: RefreshTokenRequest
    ): Response<Unit>

    /**
     * Permanently deletes the authenticated account and all of its server-side data. The account
     * is identified by the bearer token attached by
     * [com.pahntd.expensetracker.data.remote.interceptor.AuthInterceptor], so no body is sent.
     * Answers 204 on success.
     */
    @DELETE("account")
    suspend fun deleteAccount(): Response<Unit>
}
