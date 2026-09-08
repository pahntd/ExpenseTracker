package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val passwordHash: String
)