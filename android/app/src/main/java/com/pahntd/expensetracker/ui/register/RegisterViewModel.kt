package com.pahntd.expensetracker.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.auth.RegisterResult
import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.utils.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventState = MutableSharedFlow<RegisterEvent>()
    val eventState = _eventState.asSharedFlow()

    fun updateEmail(email: String) {
        _uiState.update { current ->
            current.copy(
                email = email,
                // Only re-check while an error is already visible — no eager errors while typing.
                emailError = current.emailError?.let { AuthValidator.validateEmail(email) }
            )
        }
    }

    fun updatePassword(password: String) {
        _uiState.update { current ->
            current.copy(
                password = password,
                passwordError = current.passwordError?.let { AuthValidator.validatePassword(password) },
                // Password drives the confirm match — re-check it only if it already has an error.
                confirmPasswordError = current.confirmPasswordError?.let {
                    AuthValidator.validateConfirmPassword(password, current.confirmPassword)
                }
            )
        }
    }

    fun updateConfirmPassword(confirmPassword: String) {
        _uiState.update { current ->
            current.copy(
                confirmPassword = confirmPassword,
                confirmPasswordError = current.confirmPasswordError?.let {
                    AuthValidator.validateConfirmPassword(current.password, confirmPassword)
                }
            )
        }
    }

    fun register() {
        if (_uiState.value.isLoading) return

        val current = _uiState.value
        val emailError = AuthValidator.validateEmail(current.email)
        val passwordError = AuthValidator.validatePassword(current.password)
        val confirmPasswordError = AuthValidator.validateConfirmPassword(
            current.password,
            current.confirmPassword
        )

        _uiState.update {
            it.copy(
                emailError = emailError,
                passwordError = passwordError,
                confirmPasswordError = confirmPasswordError
            )
        }

        if (emailError != null || passwordError != null || confirmPasswordError != null) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = authRepository.register(
                    email = current.email.trim(),
                    password = current.password
                )
                when (result) {
                    is RegisterResult.Success ->
                        _eventState.emit(RegisterEvent.Success(result.response))

                    RegisterResult.EmailAlreadyExists ->
                        _eventState.emit(RegisterEvent.Error("An account with this email already exists"))

                    RegisterResult.NetworkError ->
                        _eventState.emit(
                            RegisterEvent.Error("Unable to reach the server. Check your connection and try again.")
                        )

                    RegisterResult.UnknownError ->
                        _eventState.emit(RegisterEvent.Error("Something went wrong. Please try again."))
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
