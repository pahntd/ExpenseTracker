package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateCategoryRequest(
    val id: String,
    val name: String,
    val icon: String,
    val updatedAt: String
)
