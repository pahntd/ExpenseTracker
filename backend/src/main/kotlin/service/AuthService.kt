package com.pahntd.expensetracker.service

import com.pahntd.expensetracker.auth.PasswordHasher
import com.pahntd.expensetracker.model.User
import com.pahntd.expensetracker.repository.RefreshTokenRepository
import com.pahntd.expensetracker.repository.UserRepository
import java.util.UUID
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

class AuthService(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
) {
    fun register(
        email: String,
        password: String
    ): User {
        if (!isValidEmail(email)) {
            throw IllegalArgumentException("Invalid email format")
        }

        if (!isValidPassword(password)) {
            throw IllegalArgumentException(
                "Password must be at least 6 characters"
            )
        }

        val existingUser = userRepository.findByEmail(email)

        if (existingUser != null) {
            throw IllegalArgumentException("Email already exists")
        }

        val passwordHash = passwordHasher.hash(password)

        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val user = User(
            id = Uuid.random(),
            email = email,
            passwordHash = passwordHash,
            createdAt = now,
            updatedAt = now
        )

        return userRepository.create(user)
    }

    private fun isValidEmail(email: String): Boolean {
        return email.matches(
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
        )
    }

    private fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    fun login(
        email: String,
        password: String
    ): User {
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("Invalid email or password")
        val isPasswordValid = passwordHasher.verify(
            password = password,
            hash = user.passwordHash
        )
        if (!isPasswordValid) {
            throw IllegalArgumentException("Invalid email or password")
        }
        return user
    }
}