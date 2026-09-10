package com.pahntd.expensetracker.utils

/**
 * Client-side auth form validation (UX only).
 *
 * Each function returns the error message to show, or `null` when the value is valid.
 * Backend validation stays the final authority; these rules only give fast feedback
 * before a request is ever sent.
 *
 * Shared by [com.pahntd.expensetracker.ui.login.LoginViewModel] and
 * [com.pahntd.expensetracker.ui.register.RegisterViewModel].
 */
object AuthValidator {

    // Mirrors the backend rule in AuthService.isValidEmail — deliberately not full RFC 5322.
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    private const val PASSWORD_MIN_LENGTH = 6
    private const val PASSWORD_MAX_LENGTH = 64

    /** Leading/trailing whitespace is ignored for the check only; the caller keeps the raw value. */
    fun validateEmail(email: String): String? {
        val value = email.trim()
        return when {
            value.isBlank() -> "Email is required"
            !value.matches(EMAIL_REGEX) -> "Invalid email format"
            else -> null
        }
    }

    /** Validated exactly as entered — never trimmed or otherwise modified. */
    fun validatePassword(password: String): String? {
        return when {
            password.isBlank() -> "Password is required"
            password.length < PASSWORD_MIN_LENGTH -> "Password must be at least 6 characters"
            password.length > PASSWORD_MAX_LENGTH -> "Password must be at most 64 characters"
            password.none { it.isLetter() } -> "Password must contain at least one letter"
            password.none { it.isDigit() } -> "Password must contain at least one digit"
            else -> null
        }
    }

    fun validateConfirmPassword(password: String, confirmPassword: String): String? {
        return when {
            confirmPassword.isBlank() -> "Confirm password is required"
            confirmPassword != password -> "Passwords do not match"
            else -> null
        }
    }
}
