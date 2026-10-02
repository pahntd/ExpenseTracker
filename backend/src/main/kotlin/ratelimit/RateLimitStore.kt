package com.pahntd.expensetracker.ratelimit

import kotlin.time.Duration

/** Shared counter storage behind [RateLimitService]. */
interface RateLimitStore {
    /**
     * Atomically increments the counter for [key] and returns the new value. The first increment
     * starts a window of length [window]; the counter disappears when the window ends.
     *
     * @throws RateLimitStoreUnavailableException when the store cannot be reached.
     */
    suspend fun increment(key: String, window: Duration): WindowCount

    /**
     * Deletes the counter for [key]; the next increment starts a new window.
     *
     * @throws RateLimitStoreUnavailableException when the store cannot be reached.
     */
    suspend fun reset(key: String)
}

/** Counter value after an increment, and the time left until its window resets. */
data class WindowCount(
    val count: Long,
    val resetsIn: Duration
)

class RateLimitStoreUnavailableException(cause: Throwable) :
    RuntimeException("Rate-limit store is unavailable", cause)
