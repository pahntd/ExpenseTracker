package com.pahntd.expensetracker.data.remote.mapper

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.remote.dto.CategoryResponse
import com.pahntd.expensetracker.data.remote.dto.CreateCategoryRequest
import com.pahntd.expensetracker.data.remote.dto.UpdateCategoryRequest

/**
 * Maps a server category to its Room entity. The server id is reused directly as the local id
 * since local and server share the same UUID identity. Returns null when a required field the
 * server sent can't be parsed, so pull sync can skip it instead of fabricating a value.
 */
fun CategoryResponse.toEntity(): CategoryEntity? {
    val parsedUpdatedAt = updatedAt.toEpochMillisOrNull() ?: return null

    return CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        updatedAt = parsedUpdatedAt,
        syncStatus = SyncStatus.SYNCED
    )
}

/**
 * Maps a local category to the request body for creating it on the server. The locally-generated
 * id is sent so the server adopts it as the record's id, keeping local and server identity equal.
 */
fun CategoryEntity.toCreateRequest(): CreateCategoryRequest {
    return CreateCategoryRequest(
        id = id,
        name = name,
        icon = icon,
        updatedAt = updatedAt.toNetworkDateTime()
    )
}

/** Maps a local category to the request body for updating it on the server. */
fun CategoryEntity.toUpdateRequest(): UpdateCategoryRequest {
    return UpdateCategoryRequest(
        name = name,
        icon = icon,
        updatedAt = updatedAt.toNetworkDateTime()
    )
}
