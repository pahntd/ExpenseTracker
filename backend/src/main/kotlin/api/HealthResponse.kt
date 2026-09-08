package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String
)