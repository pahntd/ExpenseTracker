package com.pahntd.expensetracker.data.remote.mapper

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Parses a backend ISO-8601 offset date/time string (matches [OffsetDateTime]'s own toString
 * contract) into local epoch millis. Returns null when the string can't be parsed, so callers can
 * decide how to handle it.
 */
fun String.toEpochMillisOrNull(): Long? {
    return runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()
}

/**
 * Formats local epoch millis as the ISO-8601 offset date/time string the backend expects, always
 * in UTC so local <-> network conversions are consistent regardless of device timezone.
 */
fun Long.toNetworkDateTime(): String {
    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC).toString()
}
