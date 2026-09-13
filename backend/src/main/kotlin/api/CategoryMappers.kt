package com.pahntd.expensetracker.api

import com.pahntd.expensetracker.model.Category

fun Category.toResponse(): CategoryResponse {
    return CategoryResponse(
        id = id.toString(),
        name = name,
        icon = icon,
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}
