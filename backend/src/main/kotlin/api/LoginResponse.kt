package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String
)