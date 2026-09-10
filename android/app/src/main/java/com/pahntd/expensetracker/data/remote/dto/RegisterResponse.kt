package com.pahntd.expensetracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Successful body of POST /register. No tokens are returned or stored here. */
data class RegisterResponse(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String
)
