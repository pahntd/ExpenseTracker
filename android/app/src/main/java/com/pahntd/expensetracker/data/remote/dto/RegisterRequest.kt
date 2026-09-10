package com.pahntd.expensetracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Body for POST /register. */
data class RegisterRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)
