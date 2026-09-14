package com.pahntd.expensetracker.data.remote.mapper

import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.remote.dto.TransactionResponse
import java.time.OffsetDateTime

/**
 * Maps a server transaction to its Room entity. Returns null when the response can't be
 * reconciled with local data (unresolved category, or a field the server sent in a shape
 * this client doesn't understand) so pull sync can skip it instead of crashing.
 */
fun TransactionResponse.toEntity(categoryLocalId: Long): TransactionEntity? {
    val parsedAmount = amount.toDoubleOrNull() ?: return null
    val parsedType = runCatching { TransactionType.valueOf(type) }.getOrNull() ?: return null
    val parsedDate = runCatching { OffsetDateTime.parse(date).toInstant().toEpochMilli() }
        .getOrNull() ?: return null

    return TransactionEntity(
        amount = parsedAmount,
        type = parsedType,
        categoryId = categoryLocalId,
        date = parsedDate,
        title = title,
        serverId = id
    )
}
