package com.pahntd.expensetracker.ratelimit

import com.pahntd.expensetracker.redis.RedisDatastore
import io.lettuce.core.RedisException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fixed-window counters in Redis. INCR and the expiry run in one Lua script, which Redis executes
 * atomically, so concurrent requests on any number of backend instances each get a distinct count.
 */
class RedisRateLimitStore(
    private val redis: RedisDatastore
) : RateLimitStore {

    override suspend fun increment(key: String, window: Duration): WindowCount {
        val result = try {
            redis.evalLongs(
                script = INCREMENT_SCRIPT,
                keys = listOf(key),
                args = listOf(window.inWholeMilliseconds.toString())
            )
        } catch (e: RedisException) {
            throw RateLimitStoreUnavailableException(e)
        }

        return WindowCount(count = result[0], resetsIn = result[1].milliseconds)
    }

    override suspend fun reset(key: String) {
        try {
            redis.delete(key)
        } catch (e: RedisException) {
            throw RateLimitStoreUnavailableException(e)
        }
    }

    private companion object {
        // KEYS[1] = counter key, ARGV[1] = window in milliseconds. Returns {count, ttlMillis}.
        // The TTL check also repairs a counter that somehow lost its expiry, which would
        // otherwise block the client forever.
        const val INCREMENT_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
                ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
        """
    }
}
