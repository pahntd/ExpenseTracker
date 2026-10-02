package com.pahntd.expensetracker.ratelimit

import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * Brute-force protection per account for POST /login: 5 attempts per 60 s per normalized email
 * ([RateLimitPolicy.LOGIN_ACCOUNT]), whatever IP the attempts come from.
 *
 * Each attempt reserves a slot with an atomic increment BEFORE the credentials are checked, so
 * concurrent attempts can never verify more than [RateLimitPolicy.limit] passwords per window.
 * A failed check keeps its slot (it counts as a failure); a successful one resets the counter.
 * The net effect is "N failed attempts since the last success", without a check-then-increment race.
 *
 * Unknown emails are counted exactly like wrong passwords, so the limiter reveals nothing about
 * which accounts exist.
 */
class AccountLoginLimiter(
    private val rateLimitService: RateLimitService
) {
    /**
     * Runs [verifyCredentials] unless the account has used up its attempts.
     * A credential failure is signalled by [verifyCredentials] throwing; the exception propagates
     * unchanged, so the existing invalid-credentials response is kept.
     *
     * @throws RateLimitStoreUnavailableException when the attempt cannot be counted.
     */
    suspend fun <T> attempt(email: String, verifyCredentials: suspend () -> T): LoginAttempt<T> {
        val account = normalizeAccount(email)

        val decision = rateLimitService.tryConsume(RateLimitPolicy.LOGIN_ACCOUNT, account)
        if (decision is RateLimitDecision.Limited) {
            return LoginAttempt.Limited(decision.retryAfterSeconds)
        }

        val result = verifyCredentials()

        try {
            rateLimitService.reset(RateLimitPolicy.LOGIN_ACCOUNT, account)
        } catch (e: RateLimitStoreUnavailableException) {
            // The user proved the password; don't fail the login over a counter that expires anyway.
            logger.warn("Could not reset login attempt counter after a successful login", e)
        }
        return LoginAttempt.Success(result)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AccountLoginLimiter::class.java)

        /**
         * Bucket identity for an email: trimmed and lower-cased, so casing or surrounding spaces
         * cannot open extra buckets for one account. Only used for the rate-limit key; the user
         * lookup itself is unchanged.
         */
        fun normalizeAccount(email: String): String = email.trim().lowercase(Locale.ROOT)
    }
}

sealed interface LoginAttempt<out T> {
    data class Success<T>(val value: T) : LoginAttempt<T>
    data class Limited(val retryAfterSeconds: Long) : LoginAttempt<Nothing>
}
