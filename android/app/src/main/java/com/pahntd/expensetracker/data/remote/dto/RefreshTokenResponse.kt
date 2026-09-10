package com.pahntd.expensetracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Successful body of POST /auth/refresh. The backend does not rotate refresh tokens, so only a
 * new access token comes back.
 */
data class RefreshTokenResponse(
    @SerializedName("accessToken") val accessToken: String
)
