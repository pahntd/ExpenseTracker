package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.model.RefreshToken

interface RefreshTokenRepository {
    fun create(refreshToken: RefreshToken): RefreshToken

    fun findByToken(token: String): RefreshToken?

    fun revoke(token: String)
}