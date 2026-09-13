package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateCategoryRequest(
    val name: String,
    val icon: String
)
