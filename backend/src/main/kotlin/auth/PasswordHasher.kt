package com.pahntd.expensetracker.auth

interface PasswordHasher {
    fun hash(password: String): String

    fun verify(
        password: String,
        hash: String
    ): Boolean
}