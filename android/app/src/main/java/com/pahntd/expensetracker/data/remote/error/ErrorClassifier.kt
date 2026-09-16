package com.pahntd.expensetracker.data.remote.error

import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Classifies a [Throwable] caught around a Retrofit call into an [AppError]. Centralizes what
 * used to be independently reinvented per Repository: only [IOException] (offline, timeout,
 * connection refused, ...) maps to [AppError.Network] - everything else is classified by what it
 * actually is, not lumped in as a network failure.
 *
 * Callers are expected to let `CancellationException` pass through unclassified and rethrow it
 * (the existing pattern around every Retrofit call in this codebase) - cancellation isn't a
 * failure to classify.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is IOException -> AppError.Network
    is HttpException -> code().toAppError()
    else -> AppError.Unknown(this)
}

/** Classifies a raw HTTP status code, e.g. from a non-throwing `Response<T>` call. */
fun Int.toAppError(): AppError = when (this) {
    401 -> AppError.Unauthorized
    in 400..499 -> AppError.Client(this)
    in 500..599 -> AppError.Server(this)
    else -> AppError.Unknown()
}

/**
 * Classifies a Retrofit [Response] by its status code. Only meaningful when
 * [Response.isSuccessful] is `false`.
 */
fun Response<*>.toAppError(): AppError = code().toAppError()
