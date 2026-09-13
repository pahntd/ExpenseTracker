package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.model.Transaction
import com.pahntd.expensetracker.model.TransactionType
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

interface TransactionRepository {
    fun create(transaction: Transaction): Transaction

    fun findById(id: Uuid, userId: Uuid): Transaction?

    fun findAll(userId: Uuid): List<Transaction>

    fun existsByCategoryId(categoryId: Uuid, userId: Uuid): Boolean

    fun update(
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
