package com.pahntd.expensetracker.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.data.auth.RefreshResult
import com.pahntd.expensetracker.data.auth.session.SessionManager
import com.pahntd.expensetracker.data.network.NetworkMonitor
import com.pahntd.expensetracker.data.network.NetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Orchestrates app startup/session recovery. Decides Login vs Home only — no sync, no seeding,
 * no Room writes; those are handled independently once Home is on screen.
 *
 *  no local session                          -> [SplashDestination.Login]
 *  session exists, offline                   -> trust local session -> [SplashDestination.Home]
 *  session exists, online, refresh succeeds  -> update access token -> [SplashDestination.Home]
 *  session exists, online, refresh token invalid/expired (definitive) -> clearSession() -> [SplashDestination.Login]
 *  session exists, online, refresh fails due to network/timeout/unknown error -> keep session -> [SplashDestination.Home]
 *
 * All persistence goes through [SessionManager]; this class never touches DataStore directly.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
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
                val session = sessionManager.observeSession().first()
                if (session == null) {
                    _destination.value = SplashDestination.Login
                    return@launch
                }

                if (networkMonitor.networkState.value == NetworkState.OFFLINE) {
                    // No connectivity to validate the session against — trust what's stored
                    // locally rather than forcing the user to log in again.
                    _destination.value = SplashDestination.Home
                    return@launch
                }

                when (val result = authRepository.refresh(session.refreshToken)) {
                    is RefreshResult.Success -> {
                        // No refresh-token rotation: only the access token changes.
                        sessionManager.updateAccessToken(result.response.accessToken)
                        _destination.value = SplashDestination.Home
                    }
                    // Backend definitively rejected the refresh token: it's unrecoverable, so
                    // there's no point keeping the session around.
                    RefreshResult.InvalidRefreshToken -> {
                        sessionManager.clearSession()
                        _destination.value = SplashDestination.Login
                    }
                    // Couldn't reach the server, or got back something unexpected — this says
                    // nothing about whether the session is actually valid, so keep it and let
                    // the user in with what's cached locally.
                    RefreshResult.NetworkError,
                    RefreshResult.UnknownError -> {
                        _destination.value = SplashDestination.Home
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
