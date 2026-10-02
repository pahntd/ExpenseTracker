package com.pahntd.expensetracker.ratelimit

class RateLimitService(
    private val store: RateLimitStore
) {
    /**
     * Counts one request from [clientIp] against [policy]. Every request is counted, including
     * rejected ones; the window does not move, so the client is allowed again when it ends.
     */
    suspend fun tryConsume(policy: RateLimitPolicy, clientIp: String): RateLimitDecision {
        val window = store.increment(policy.keyFor(clientIp), policy.window)

        if (window.count <= policy.limit) {
            return RateLimitDecision.Allowed(remaining = (policy.limit - window.count).toInt())
        }

        // Round up so a client that waits Retry-After seconds always lands in the new window.
        val retryAfterSeconds = (window.resetsIn.inWholeMilliseconds + 999) / 1000
        return RateLimitDecision.Limited(retryAfterSeconds = retryAfterSeconds.coerceAtLeast(1))
    }
}

sealed interface RateLimitDecision {
    data class Allowed(val remaining: Int) : RateLimitDecision
    data class Limited(val retryAfterSeconds: Long) : RateLimitDecision
}
