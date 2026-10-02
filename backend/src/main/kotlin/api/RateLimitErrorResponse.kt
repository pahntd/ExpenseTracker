package com.pahntd.expensetracker.api

import kotlinx.serialization.Serializable

/** 429 body. Keeps `error` from [ErrorResponse] so existing error parsing still works. */
@Serializable
data class RateLimitErrorResponse(
    val error: String,
    val code: String,
    val retryAfterSeconds: Long
)
