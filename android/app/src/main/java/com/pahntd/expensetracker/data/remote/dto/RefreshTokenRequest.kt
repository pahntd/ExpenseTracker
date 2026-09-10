package com.pahntd.expensetracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Body of POST /auth/refresh. */
data class RefreshTokenRequest(
    @SerializedName("refreshToken") val refreshToken: String
)
