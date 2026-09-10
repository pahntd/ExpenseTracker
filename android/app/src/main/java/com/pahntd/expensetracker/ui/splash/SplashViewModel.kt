package com.pahntd.expensetracker.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.DatabaseInitializer
import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.data.auth.RefreshResult
import com.pahntd.expensetracker.data.auth.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Orchestrates app startup: seed local data, then restore the auth session.
 *
 *  observeSession() == null            -> [SplashDestination.Login]
 *  observeSession() != null, refresh OK -> update access token -> [SplashDestination.Home]
 *  refresh fails (any reason)          -> clearSession() -> [SplashDestination.Login]
 *
 * All persistence goes through [SessionManager]; this class never touches DataStore directly.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val initializer: DatabaseInitializer,
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination?>(null)

    /** `null` while startup is still running; set once the navigation target is known. */
    val destination = _destination.asStateFlow()

    private var started = false

    /** Runs the startup flow once per ViewModel instance. */
    fun start() {
        if (started) return
        started = true

        viewModelScope.launch {
            try {
                initializer.seed()

                val session = sessionManager.observeSession().first()
                if (session == null) {
                    _destination.value = SplashDestination.Login
                    return@launch
                }

                when (val result = authRepository.refresh(session.refreshToken)) {
                    is RefreshResult.Success -> {
                        // No refresh-token rotation: only the access token changes.
                        sessionManager.updateAccessToken(result.response.accessToken)
                        _destination.value = SplashDestination.Home
                    }
                    // Invalid / expired / revoked / malformed refresh token, no connectivity, or
                    // any other failure: don't keep a half-usable session — reset and go to Login.
                    RefreshResult.InvalidRefreshToken,
                    RefreshResult.NetworkError,
                    RefreshResult.UnknownError -> {
                        sessionManager.clearSession()
                        _destination.value = SplashDestination.Login
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never strand the user on Splash if startup hits something unexpected.
                _destination.value = SplashDestination.Login
            }
        }
    }
}
