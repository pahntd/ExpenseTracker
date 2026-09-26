package com.pahntd.expensetracker.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.auth.AuthRepository
import com.pahntd.expensetracker.data.auth.RefreshResult
import com.pahntd.expensetracker.data.auth.session.SessionManager
import com.pahntd.expensetracker.data.network.NetworkMonitor
import com.pahntd.expensetracker.data.network.NetworkState
import com.pahntd.expensetracker.data.sync.SyncScheduler
import com.pahntd.expensetracker.data.sync.SyncTrigger
import com.pahntd.expensetracker.utils.AccountPreferencesCleaner
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
 *  session exists, online, refresh token invalid/expired (definitive) -> clearSession() + account prefs -> [SplashDestination.Login]
 *  session exists, online, refresh fails due to network/timeout/unknown error -> keep session -> [SplashDestination.Home]
 *
 * Session persistence goes through [SessionManager]; this class never touches DataStore directly.
 * Account-scoped preferences are cleared through [AccountPreferencesCleaner].
 *
 * Every path that lands on [SplashDestination.Home] also requests [SyncTrigger.STARTUP] via
 * [syncScheduler] - this only schedules background work (subject to WorkManager's own
 * `NetworkType.CONNECTED` constraint), it never delays navigation. Home continues to render from
 * Room immediately; sync updates it later, in the background.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
    private val syncScheduler: SyncScheduler,
    private val accountPreferencesCleaner: AccountPreferencesCleaner,
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
                    navigateHome()
                    return@launch
                }

                when (val result = authRepository.refresh(session.refreshToken)) {
                    is RefreshResult.Success -> {
                        // No refresh-token rotation: only the access token changes.
                        sessionManager.updateAccessToken(result.response.accessToken)
                        navigateHome()
                    }
                    // Backend definitively rejected the refresh token: it's unrecoverable, so
                    // there's no point keeping the session around.
                    RefreshResult.InvalidRefreshToken -> {
                        sessionManager.clearSessionAndRememberUser()
                        // Forced logout: same account-scoped cleanup (incl. unlocks) as a manual one.
                        accountPreferencesCleaner.clear()
                        _destination.value = SplashDestination.Login
                    }
                    // Couldn't reach the server, or got back something unexpected — this says
                    // nothing about whether the session is actually valid, so keep it and let
                    // the user in with what's cached locally.
                    RefreshResult.NetworkError,
                    RefreshResult.UnknownError -> {
                        navigateHome()
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

    /**
     * Every path to Home has a session worth syncing - request [SyncTrigger.STARTUP] before
     * navigating. [SyncScheduler.enqueueSync] only enqueues WorkManager work and returns
     * immediately; it never blocks this navigation decision.
     */
    private fun navigateHome() {
        syncScheduler.enqueueSync(SyncTrigger.STARTUP)
        _destination.value = SplashDestination.Home
    }
}
