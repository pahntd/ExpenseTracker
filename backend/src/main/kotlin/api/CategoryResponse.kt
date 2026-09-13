package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class CategoryResponse(
    val id: String,
    val name: String,
    val icon: String,
    val createdAt: String,
    val updatedAt: String
)
