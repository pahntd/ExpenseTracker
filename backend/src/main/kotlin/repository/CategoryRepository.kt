package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.model.Category
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

interface CategoryRepository {
    fun create(category: Category): Category

    fun findById(id: Uuid, userId: Uuid): Category?

    fun findAll(userId: Uuid): List<Category>

    fun findByUserIdAndName(userId: Uuid, name: String): Category?

    fun update(
        id: Uuid,
        userId: Uuid,
        name: String,
        icon: String,
        updatedAt: OffsetDateTime
    ): Category?

    fun delete(id: Uuid, userId: Uuid): Boolean
}
