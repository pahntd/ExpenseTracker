package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String
)