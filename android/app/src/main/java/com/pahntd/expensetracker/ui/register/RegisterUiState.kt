package com.pahntd.expensetracker.ui.register

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    /** A 429's Retry-After window is running: the Register action stays disabled until it ends. */
    val isRateLimited: Boolean = false
)
