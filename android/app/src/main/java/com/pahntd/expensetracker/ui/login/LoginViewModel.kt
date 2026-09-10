package com.pahntd.expensetracker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.data.auth.LoginResult
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
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventState = MutableSharedFlow<LoginEvent>()
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
                passwordError = current.passwordError?.let { AuthValidator.validatePassword(password) }
            )
        }
    }

    fun login() {
        if (_uiState.value.isLoading) return

        val current = _uiState.value
        val emailError = AuthValidator.validateEmail(current.email)
        val passwordError = AuthValidator.validatePassword(current.password)

        _uiState.update {
            it.copy(
                emailError = emailError,
                passwordError = passwordError
            )
        }

        if (emailError != null || passwordError != null) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = authRepository.login(
                    email = current.email.trim(),
                    password = current.password
                )
                when (result) {
                    is LoginResult.Success ->
                        _eventState.emit(LoginEvent.Success(result.response))

                    LoginResult.InvalidCredentials ->
                        _eventState.emit(LoginEvent.Error("Invalid email or password"))

                    LoginResult.NetworkError ->
                        _eventState.emit(
                            LoginEvent.Error("Unable to reach the server. Check your connection and try again.")
                        )

                    LoginResult.UnknownError ->
                        _eventState.emit(LoginEvent.Error("Something went wrong. Please try again."))
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}
