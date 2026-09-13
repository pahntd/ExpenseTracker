package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class TransactionResponse(
    val id: String,
    val amount: String,
    val type: String,
    val categoryId: String?,
    val date: String,
    val title: String?,
    val createdAt: String,
    val updatedAt: String
)
