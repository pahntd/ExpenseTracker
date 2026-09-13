package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class UpdateTransactionRequest(
    val amount: String,
    val type: String,
    val categoryId: String? = null,
    val date: String,
    val title: String? = null
)
