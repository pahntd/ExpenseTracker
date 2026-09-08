package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email:String,
    val password: String
)
