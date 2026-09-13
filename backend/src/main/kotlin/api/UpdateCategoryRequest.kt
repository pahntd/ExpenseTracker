package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class UpdateCategoryRequest(
    val name: String,
    val icon: String
)
