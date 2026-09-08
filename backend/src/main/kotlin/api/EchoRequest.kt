package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

@Serializable
data class EchoRequest(
    val message: String
)