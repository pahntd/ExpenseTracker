package com.pahntd.expensetracker.ratelimit

class RateLimitService(
    private val store: RateLimitStore
) {
    /**
     * Counts one request from [subject] (client IP or account) against [policy]. Every request is
     * counted, including rejected ones; the window does not move, so the subject is allowed again
     * when it ends.
     */
    suspend fun tryConsume(policy: RateLimitPolicy, subject: String): RateLimitDecision {
        val window = store.increment(policy.keyFor(subject), policy.window)

        if (window.count <= policy.limit) {
            return RateLimitDecision.Allowed(remaining = (policy.limit - window.count).toInt())
        }

        // Round up so a client that waits Retry-After seconds always lands in the new window.
        val retryAfterSeconds = (window.resetsIn.inWholeMilliseconds + 999) / 1000
        return RateLimitDecision.Limited(retryAfterSeconds = retryAfterSeconds.coerceAtLeast(1))
    }

    /** Drops the counter of [subject] for [policy], giving it a fresh window on the next request. */
    suspend fun reset(policy: RateLimitPolicy, subject: String) {
        store.reset(policy.keyFor(subject))
    }
}

sealed interface RateLimitDecision {
    data class Allowed(val remaining: Int) : RateLimitDecision
    data class Limited(val retryAfterSeconds: Long) : RateLimitDecision
}
