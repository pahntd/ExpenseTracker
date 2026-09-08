package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class EchoResponse(
    val message: String
)