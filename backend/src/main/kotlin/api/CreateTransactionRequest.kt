package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class CreateTransactionRequest(
    val amount: String,
    val type: String,
    val categoryId: String? = null,
    val date: String,
    val title: String? = null
)
