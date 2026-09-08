package com.pahntd.expensetracker.auth

import org.mindrot.jbcrypt.BCrypt

class BCryptPasswordHasher : PasswordHasher {
    override fun hash(password: String): String {
        return BCrypt.hashpw(
            password,
            BCrypt.gensalt()
        )
    }

    override fun verify(password: String, hash: String): Boolean {
        return BCrypt.checkpw(password, hash)
    }
}