package com.pahntd.expensetracker.data.remote.mapper

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.remote.dto.CategoryResponse
import java.time.OffsetDateTime

/**
 * Maps a server category to its Room entity. The server id is reused directly as the local id
 * since local and server share the same UUID identity.
 */
fun CategoryResponse.toEntity(): CategoryEntity {
    val parsedUpdatedAt = runCatching { OffsetDateTime.parse(updatedAt).toInstant().toEpochMilli() }
        .getOrDefault(System.currentTimeMillis())

    return CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        updatedAt = parsedUpdatedAt,
        syncStatus = SyncStatus.SYNCED
    )
}
