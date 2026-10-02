package com.pahntd.expensetracker.data.remote.error

import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** HTTP 429 Too Many Requests - not defined in [java.net.HttpURLConnection]. */
const val HTTP_TOO_MANY_REQUESTS = 429

private const val HEADER_RETRY_AFTER = "Retry-After"
private const val BODY_RETRY_AFTER_SECONDS = "retryAfterSeconds"

/**
 * Classifies a [Throwable] caught around a Retrofit call into an [AppError]. Centralizes what
 * used to be independently reinvented per Repository: only [IOException] (offline, timeout,
 * connection refused, ...) maps to [AppError.Network] - everything else is classified by what it
 * actually is, not lumped in as a network failure.
 *
 * [RefreshRateLimitedException] is an [IOException] only because OkHttp's `Authenticator` can
 * throw nothing else; it is checked first so it never collapses into [AppError.Network].
 *
 * Callers are expected to let `CancellationException` pass through unclassified and rethrow it
 * (the existing pattern around every Retrofit call in this codebase) - cancellation isn't a
 * failure to classify.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is RefreshRateLimitedException -> AppError.RateLimited(retryAfterSeconds)
    is IOException -> AppError.Network
    is HttpException ->
        if (code() == HTTP_TOO_MANY_REQUESTS) AppError.RateLimited(retryAfterSeconds())
        else code().toAppError()
    else -> AppError.Unknown(this)
}

/**
 * Classifies a raw HTTP status code, e.g. from a non-throwing `Response<T>` call. A bare code
 * carries no `Retry-After`, so prefer the [Response]/[HttpException] overloads when available.
 */
fun Int.toAppError(): AppError = when (this) {
    401 -> AppError.Unauthorized
    HTTP_TOO_MANY_REQUESTS -> AppError.RateLimited(retryAfterSeconds = null)
    in 400..499 -> AppError.Client(this)
    in 500..599 -> AppError.Server(this)
    else -> AppError.Unknown()
}

/**
 * Classifies a Retrofit [Response] by its status code. Only meaningful when
 * [Response.isSuccessful] is `false`.
 */
fun Response<*>.toAppError(): AppError =
    if (code() == HTTP_TOO_MANY_REQUESTS) AppError.RateLimited(retryAfterSeconds())
    else code().toAppError()

/** See [Response.retryAfterSeconds]. `null` if the exception carries no response. */
fun HttpException.retryAfterSeconds(): Long? = response()?.retryAfterSeconds()

/**
 * How long a 429 [Response] asks the client to wait, in seconds: the `Retry-After` header first
 * (delta-seconds or an HTTP date), falling back to the backend's `retryAfterSeconds` JSON field.
 * `null` when neither is present or parseable - callers must not invent a wait time.
 *
 * Reads (and so consumes) [Response.errorBody] when the header is missing.
 */
fun Response<*>.retryAfterSeconds(): Long? =
    headers().retryAfterSeconds() ?: errorBody().retryAfterSeconds()

private fun Headers.retryAfterSeconds(): Long? {
    val value = get(HEADER_RETRY_AFTER)?.trim() ?: return null
    value.toLongOrNull()?.let { return it.takeIf { seconds -> seconds >= 0 } }
    val date = getDate(HEADER_RETRY_AFTER) ?: return null
    val millisUntil = date.time - System.currentTimeMillis()
    // Round up so a date a fraction of a second away still waits rather than reporting 0.
    return (millisUntil.coerceAtLeast(0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
}

private fun ResponseBody?.retryAfterSeconds(): Long? {
    if (this == null) return null
    return try {
        val element = JsonParser.parseString(string())
            .takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get(BODY_RETRY_AFTER_SECONDS)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        element?.asLong?.takeIf { it >= 0 }
    } catch (e: Exception) {
        // Malformed / non-JSON error body: no usable value, never a crash.
        null
    }
}

private const val MILLIS_PER_SECOND = 1_000L
