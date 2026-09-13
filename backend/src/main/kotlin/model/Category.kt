package com.pahntd.expensetracker.model

import java.time.OffsetDateTime
import kotlin.uuid.Uuid

data class Category(
    val id: Uuid,
    val userId: Uuid,
    val name: String,
    val icon: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
