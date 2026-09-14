package com.pahntd.expensetracker.data.remote.mapper

import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.remote.dto.CategoryResponse

fun CategoryResponse.toEntity(): CategoryEntity {
    return CategoryEntity(
        name = name,
        icon = icon,
        serverId = id
    )
}
