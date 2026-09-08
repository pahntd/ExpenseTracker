package com.pahntd.expensetracker.auth

import java.security.SecureRandom
import java.util.Base64

class RefreshTokenGenerator {

    private val secureRandom = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }
}