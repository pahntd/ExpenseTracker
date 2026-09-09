package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.database.table.RefreshTokenTable
import com.pahntd.expensetracker.model.RefreshToken
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

class ExposedRefreshTokenRepository : RefreshTokenRepository {

    override fun create(refreshToken: RefreshToken): RefreshToken {
        return transaction {
            RefreshTokenTable.insert {
                it[id] = refreshToken.id
                it[userId] = refreshToken.userId
                it[token] = refreshToken.token
                it[expiresAt] = refreshToken.expiresAt
                it[createdAt] = refreshToken.createdAt
                it[revokedAt] = refreshToken.revokedAt
            }

            refreshToken
        }
    }

    override fun findByToken(
        token: String
    ): RefreshToken? {

        return transaction {

            RefreshTokenTable
                .selectAll()
                .where {
                    RefreshTokenTable.token eq token
                }
                .singleOrNull()
                ?.let { row ->

                    RefreshToken(
                        id = row[RefreshTokenTable.id],
                        userId = row[RefreshTokenTable.userId],
                        token = row[RefreshTokenTable.token],
                        expiresAt = row[RefreshTokenTable.expiresAt],
                        createdAt = row[RefreshTokenTable.createdAt],
                        revokedAt = row[RefreshTokenTable.revokedAt]
                    )
                }
        }
    }

    override fun revoke(token: String) {
        transaction {
            RefreshTokenTable.update(
                where = {
                    RefreshTokenTable.token eq token
                }
            ) {
                it[revokedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }
}