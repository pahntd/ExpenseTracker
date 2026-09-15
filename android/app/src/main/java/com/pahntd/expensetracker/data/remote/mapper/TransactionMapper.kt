package com.pahntd.expensetracker.data.remote.mapper

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.remote.dto.CreateTransactionRequest
import com.pahntd.expensetracker.data.remote.dto.TransactionResponse
import com.pahntd.expensetracker.data.remote.dto.UpdateTransactionRequest

/**
 * Maps a server transaction to its Room entity. The server id is reused directly as the local id,
 * and categoryId is reused as-is since categories share the same UUID identity locally and on the
 * server. categoryId is nullable both remotely and locally, so a transaction with no category is
 * mapped as-is rather than discarded. Returns null when a field the server sent can't be parsed,
 * so pull sync can skip it instead of crashing.
 */
fun TransactionResponse.toEntity(): TransactionEntity? {
    val parsedAmount = amount.toDoubleOrNull() ?: return null
    val parsedType = runCatching { TransactionType.valueOf(type) }.getOrNull() ?: return null
    val parsedDate = date.toEpochMillisOrNull() ?: return null
    val parsedUpdatedAt = updatedAt.toEpochMillisOrNull() ?: return null

    return TransactionEntity(
        id = id,
        amount = parsedAmount,
        type = parsedType,
        categoryId = categoryId,
        date = parsedDate,
        title = title,
        updatedAt = parsedUpdatedAt,
        syncStatus = SyncStatus.SYNCED
    )
}

/**
 * Maps a local transaction to the request body for creating it on the server. The
 * locally-generated id is sent so the server adopts it as the record's id, keeping local and
 * server identity equal.
 */
fun TransactionEntity.toCreateRequest(): CreateTransactionRequest {
    return CreateTransactionRequest(
        id = id,
        amount = amount.toNetworkAmount(),
        type = type.name,
        categoryId = categoryId,
        date = date.toNetworkDateTime(),
        title = title,
        updatedAt = updatedAt.toNetworkDateTime()
    )
}

/** Maps a local transaction to the request body for updating it on the server. */
fun TransactionEntity.toUpdateRequest(): UpdateTransactionRequest {
    return UpdateTransactionRequest(
        amount = amount.toNetworkAmount(),
        type = type.name,
        categoryId = categoryId,
        date = date.toNetworkDateTime(),
        title = title,
        updatedAt = updatedAt.toNetworkDateTime()
    )
}
