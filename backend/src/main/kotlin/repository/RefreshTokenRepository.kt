package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.model.RefreshToken
import kotlin.uuid.Uuid

interface RefreshTokenRepository {
    fun create(refreshToken: RefreshToken): RefreshToken

    fun findByToken(token: String): RefreshToken?

    fun revoke(token: String)

    fun deleteAllByUserId(userId: Uuid): Int
}