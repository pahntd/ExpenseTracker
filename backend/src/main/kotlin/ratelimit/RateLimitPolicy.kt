package com.pahntd.expensetracker.ratelimit

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fixed-window limits. Each policy has its own Redis bucket, so traffic on one endpoint (or one
 * kind of subject) never consumes the quota of another.
 */
enum class RateLimitPolicy(
    val bucket: String,
    val subject: String,
    val limit: Int,
    val window: Duration
) {
    LOGIN(bucket = "login", subject = "ip", limit = 5, window = 60.seconds),
    REGISTER(bucket = "register", subject = "ip", limit = 5, window = 60.seconds),
    REFRESH(bucket = "refresh", subject = "ip", limit = 10, window = 60.seconds),

    /** Login attempts per account, independent of the client IP. See [AccountLoginLimiter]. */
    LOGIN_ACCOUNT(bucket = "login", subject = "account", limit = 5, window = 60.seconds);

    /** Redis key, e.g. `rl:login:ip:203.0.113.7` or `rl:login:account:user@example.com`. */
    fun keyFor(subjectValue: String): String = "rl:$bucket:$subject:$subjectValue"
}
