package com.pahntd.expensetracker.ratelimit

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fixed-window limits per client IP. Each policy has its own Redis bucket, so traffic on one
 * endpoint never consumes the quota of another.
 */
enum class RateLimitPolicy(
    val bucket: String,
    val limit: Int,
    val window: Duration
) {
    LOGIN(bucket = "login", limit = 5, window = 60.seconds),
    REGISTER(bucket = "register", limit = 5, window = 60.seconds),
    REFRESH(bucket = "refresh", limit = 10, window = 60.seconds);

    /** Redis key, e.g. `rl:login:ip:203.0.113.7`. */
    fun keyFor(clientIp: String): String = "rl:$bucket:ip:$clientIp"
}
