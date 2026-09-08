package com.pahntd.expensetracker.model

import java.time.OffsetDateTime
import kotlin.uuid.Uuid

data class RefreshToken(
    val id: Uuid,
    val userId: Uuid,
    val token: String,
    val expiresAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?
)