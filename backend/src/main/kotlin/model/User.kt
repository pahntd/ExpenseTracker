package com.pahntd.expensetracker.model

import java.time.OffsetDateTime
import kotlin.uuid.Uuid

data class User(
    val id: Uuid,
    val email: String,
    val passwordHash: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
