package com.pahntd.expensetracker.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.data.auth.LoginResult
import com.pahntd.expensetracker.data.auth.session.SessionManager
import com.pahntd.expensetracker.data.repository.LocalAccountDataCleaner
import com.pahntd.expensetracker.data.sync.SyncScheduler
import com.pahntd.expensetracker.data.sync.SyncTrigger
import com.pahntd.expensetracker.utils.AccountPreferencesCleaner
import com.pahntd.expensetracker.utils.AuthValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
    private val syncScheduler: SyncScheduler,
    private val localAccountDataCleaner: LocalAccountDataCleaner,
    private val accountPreferencesCleaner: AccountPreferencesCleaner
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
                    is LoginResult.Success -> {
                        val response = result.response
                        try {
                            prepareLocalStateFor(response.userId)
                            // Persist the session first; only report success once it is stored.
                            // This also records response.userId as the new last user id.
                            sessionManager.saveSession(
                                userId = response.userId,
                                accessToken = response.accessToken,
                                refreshToken = response.refreshToken
                            )
                            // Fire-and-forget: enqueueSync only schedules background work, it
                            // never blocks the login flow on the sync itself.
                            syncScheduler.enqueueSync(SyncTrigger.LOGIN)
                            // Re-arms the periodic safety net in case a prior logout in this same
                            // process cancelled it - KEEP makes this a no-op otherwise.
                            syncScheduler.schedulePeriodicSync()
                            _eventState.emit(LoginEvent.Success(response))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _eventState.emit(LoginEvent.Error("Something went wrong. Please try again."))
                        }
                    }

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

    /**
     * Runs before the new session is saved (and so before its sync is enqueued):
     *
     * - Account-scoped preferences are always reset: no previous username, default Statistics
     *   time filter, every feature locked. Unlocks are never kept per user.
     * - Room is wiped (sync cancelled first) unless the last user id on this device is exactly
     *   [newUserId]. A forced logout keeps that id and leaves Room alone, so the same user logging
     *   back in keeps their `PENDING_*` rows and the login sync pushes them to their own account;
     *   a different user - or no known last user (fresh install, after a manual logout) - never
     *   gets a session while another account's rows are still in Room.
     */
    private suspend fun prepareLocalStateFor(newUserId: String) {
        val lastUserId = sessionManager.getLastUserId()
        accountPreferencesCleaner.clear()
        if (lastUserId != newUserId) {
            localAccountDataCleaner.wipe()
        }
    }
}
