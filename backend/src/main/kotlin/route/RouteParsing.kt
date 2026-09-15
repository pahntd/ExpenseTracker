package com.pahntd.expensetracker.route

import com.pahntd.expensetracker.model.TransactionType
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.uuid.Uuid

fun parseUuid(value: String?): Uuid {
    val raw = value ?: throw IllegalArgumentException("Missing id")
    return try {
        Uuid.parse(raw)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid id format: $raw")
    }
}

fun parseOptionalUuid(value: String?): Uuid? {
    return value?.let { parseUuid(it) }
}

fun parseAmount(value: String): BigDecimal {
    return try {
        BigDecimal(value)
    } catch (e: NumberFormatException) {
        throw IllegalArgumentException("Invalid amount format: $value")
    }
}

fun parseTransactionType(value: String): TransactionType {
    return try {
        TransactionType.valueOf(value)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid transaction type: $value")
    }
}

fun parseOffsetDateTime(value: String): OffsetDateTime {
    return try {
        OffsetDateTime.parse(value)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Invalid date format: $value")
    }
}
