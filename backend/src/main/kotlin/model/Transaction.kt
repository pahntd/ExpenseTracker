package com.pahntd.expensetracker.model

import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

data class Transaction(
    val id: Uuid,
    val userId: Uuid,
    val amount: BigDecimal,
    val type: TransactionType,
    val categoryId: Uuid?,
    val date: OffsetDateTime,
    val title: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
