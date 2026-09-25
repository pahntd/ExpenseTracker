package com.pahntd.expensetracker.data.remote.dto

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("userId") val userId: String,
    // Nullable: Gson leaves it null (ignoring Kotlin nullability) if a backend omits the field.
    @SerializedName("email") val email: String?,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String
)
