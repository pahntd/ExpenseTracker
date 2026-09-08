package com.pahntd.expensetracker.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date
import kotlin.uuid.Uuid

class TokenService {

    private val algorithm = Algorithm.HMAC256(JwtConfig.secret)

    fun generateAccessToken(userId: Uuid): String {

        val now = System.currentTimeMillis()

        val expiration =
            now + JwtConfig.accessTokenExpirationMinutes * 60 * 1000

        return JWT.create()
            .withIssuer(JwtConfig.issuer)
            .withAudience(JwtConfig.audience)
            .withClaim("userId", userId.toString())
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(expiration))
            .sign(algorithm)
    }
}