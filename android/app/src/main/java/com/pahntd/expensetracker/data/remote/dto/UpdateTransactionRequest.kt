package com.pahntd.expensetracker.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UpdateTransactionRequest(
    @SerializedName("amount") val amount: String,
    @SerializedName("type") val type: String,
    @SerializedName("categoryId") val categoryId: String?,
    @SerializedName("date") val date: String,
    @SerializedName("title") val title: String?
)
