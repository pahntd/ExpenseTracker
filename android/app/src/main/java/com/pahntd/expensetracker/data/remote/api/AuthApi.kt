package com.pahntd.expensetracker.data.remote.api

import com.pahntd.expensetracker.data.remote.dto.RegisterRequest
import com.pahntd.expensetracker.data.remote.dto.RegisterResponse
import com.pahntd.expensetracker.data.remote.dto.LoginRequest
import com.pahntd.expensetracker.data.remote.dto.LoginResponse
import com.pahntd.expensetracker.data.remote.dto.RefreshTokenRequest
import com.pahntd.expensetracker.data.remote.dto.RefreshTokenResponse
import retrofit2.http.Body
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
}
