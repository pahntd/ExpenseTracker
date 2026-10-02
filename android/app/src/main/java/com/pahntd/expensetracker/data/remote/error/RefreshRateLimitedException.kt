package com.pahntd.expensetracker.data.remote.error

import java.io.IOException

/**
 * Thrown by [AuthAuthenticator][com.pahntd.expensetracker.data.remote.authenticator.AuthAuthenticator]
 * when the original request got a 401 but the token could not be refreshed because `/auth/refresh`
 * is rate limited (it answered 429, or its `Retry-After` cooldown is still running).
 *
 * `Authenticator.authenticate()` may only signal failure by returning `null` (which surfaces the
 * original 401, indistinguishable from a rejected refresh token) or by throwing an [IOException].
 * Throwing this lets the original call fail with a typed rate-limit error instead - classified as
 * [AppError.RateLimited] by [toAppError] - without replaying it with the stale access token.
 */
class RefreshRateLimitedException(
    val retryAfterSeconds: Long?,
) : IOException("Token refresh is rate limited")
