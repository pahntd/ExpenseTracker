package com.pahntd.expensetracker.data.auth

import android.os.SystemClock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide "don't call `/auth/refresh` before ..." gate, set from the backend's `Retry-After`
 * whenever a refresh answers 429. Shared by both refresh paths -
 * [com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator] (background 401
 * recovery) and [AuthRepository.refresh] (Splash, account deletion) - so neither can hammer the
 * endpoint while the backend is rate limiting it.
 *
 * Purely in memory: a rate limit is short-lived and a fresh process may simply try again. Uses
 * [SystemClock.elapsedRealtime] so wall-clock changes can't shorten or extend the cooldown, and a
 * single [AtomicLong] so it is safe from OkHttp dispatcher threads and coroutines alike.
 */
@Singleton
class RefreshCooldown @Inject constructor() {

    /** Elapsed-realtime millis before which refresh must not be attempted; 0 = no cooldown. */
    private val blockedUntilElapsedMillis = AtomicLong(0L)

    /**
     * Starts (or extends) the cooldown for [retryAfterSeconds]. A `null` value - the backend sent
     * no usable `Retry-After` - records nothing: no wait time is invented.
     */
    fun start(retryAfterSeconds: Long?) {
        if (retryAfterSeconds == null || retryAfterSeconds <= 0) return
        val until = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(retryAfterSeconds)
        // Never shorten a longer cooldown already in place.
        blockedUntilElapsedMillis.accumulateAndGet(until, ::maxOf)
    }

    /** Whole seconds (rounded up) left in the cooldown, or `null` if refresh is allowed now. */
    fun remainingSeconds(): Long? {
        val remainingMillis = blockedUntilElapsedMillis.get() - SystemClock.elapsedRealtime()
        if (remainingMillis <= 0) return null
        return TimeUnit.MILLISECONDS.toSeconds(remainingMillis + TimeUnit.SECONDS.toMillis(1) - 1)
    }
}
