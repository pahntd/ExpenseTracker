package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.model.Transaction
import com.pahntd.expensetracker.model.TransactionType
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

interface TransactionRepository {
    fun create(transaction: Transaction): Transaction

    fun findById(id: Uuid, userId: Uuid): Transaction?

    /** Unscoped lookup, used only for UUID-collision/ownership checks on create. */
    fun findById(id: Uuid): Transaction?

    fun findAll(userId: Uuid): List<Transaction>

    fun existsByCategoryId(categoryId: Uuid, userId: Uuid): Boolean

    /**
     * Applies Last-Edit-Wins: updates only if [updatedAt] is strictly newer than the
     * stored value. Returns the updated row, or null if the row doesn't exist or the
     * incoming [updatedAt] was not newer (stale).
     */
    fun updateIfNewer(
        id: Uuid,
        userId: Uuid,
        amount: BigDecimal,
        type: TransactionType,
        categoryId: Uuid?,
        date: OffsetDateTime,
        title: String?,
        updatedAt: OffsetDateTime
    ): Transaction?

    fun delete(id: Uuid, userId: Uuid): Boolean
}
