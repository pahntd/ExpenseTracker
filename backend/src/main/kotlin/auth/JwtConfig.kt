package com.pahntd.expensetracker.auth

object JwtConfig {
    const val issuer = "expense-tracker"
    const val audience = "expense-tracker-app"

    const val accessTokenExpirationMinutes = 15L
    const val refreshTokenExpirationDays = 30L

    val secret: String
        get() = System.getenv("JWT_SECRET")
            ?: error("JWT_SECRET environment variable is not set")
}