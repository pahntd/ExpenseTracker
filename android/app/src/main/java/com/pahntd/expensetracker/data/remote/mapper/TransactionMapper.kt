package com.pahntd.expensetracker.data.remote.mapper

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.remote.dto.TransactionResponse
import java.time.OffsetDateTime

/**
 * Maps a server transaction to its Room entity. The server id is reused directly as the local id,
 * and categoryId is reused as-is since categories share the same UUID identity locally and on the
 * server. Returns null when a field the server sent can't be parsed, so pull sync can skip it
 * instead of crashing.
 */
fun TransactionResponse.toEntity(): TransactionEntity? {
    val parsedAmount = amount.toDoubleOrNull() ?: return null
    val parsedType = runCatching { TransactionType.valueOf(type) }.getOrNull() ?: return null
    val parsedDate = runCatching { OffsetDateTime.parse(date).toInstant().toEpochMilli() }
        .getOrNull() ?: return null
    val parsedUpdatedAt = runCatching { OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())

    return TransactionEntity(
        id = id,
        amount = parsedAmount,
        type = parsedType,
        categoryId = categoryId ?: return null,
        date = parsedDate,
        title = title,
        updatedAt = parsedUpdatedAt,
        syncStatus = SyncStatus.SYNCED
    )
}
