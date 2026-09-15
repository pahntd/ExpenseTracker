package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.model.Category
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

interface CategoryRepository {
    fun create(category: Category): Category

    fun findById(id: Uuid, userId: Uuid): Category?

    /** Unscoped lookup, used only for UUID-collision/ownership checks on create. */
    fun findById(id: Uuid): Category?

    fun findAll(userId: Uuid): List<Category>

    fun findByUserIdAndName(userId: Uuid, name: String): Category?

    /**
     * Applies Last-Edit-Wins: updates only if [updatedAt] is strictly newer than the
     * stored value. Returns the updated row, or null if the row doesn't exist or the
     * incoming [updatedAt] was not newer (stale).
     */
    fun updateIfNewer(
        id: Uuid,
        userId: Uuid,
        name: String,
        icon: String,
        updatedAt: OffsetDateTime
    ): Category?

    fun delete(id: Uuid, userId: Uuid): Boolean
}
