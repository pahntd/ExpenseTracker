package com.pahntd.expensetracker.data.remote.dto

import com.google.gson.annotations.SerializedName

data class UpdateCategoryRequest(
    @SerializedName("name") val name: String,
    @SerializedName("icon") val icon: String
)
