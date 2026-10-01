package com.pahntd.expensetracker.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.auth.DeleteAccountResult
import com.pahntd.expensetracker.data.repository.SettingRepository
import com.pahntd.expensetracker.data.sync.SyncScheduler
import com.pahntd.expensetracker.data.sync.SyncTrigger
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
class SettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventState = MutableSharedFlow<SettingEventState>()
    val eventState = _eventState.asSharedFlow()

    /** Tapped from Settings: warn first when there are unsynced local changes, else log out directly. */
    fun onLogoutClick() {
        if (_uiState.value.isDeletingAccount) return
        viewModelScope.launch {
            if (settingRepository.hasPendingChanges()) {
                _eventState.emit(SettingEventState.PendingChangesWarning)
            } else {
                proceedWithLogout()
            }
        }
    }

    /** Confirmed from the [SettingEventState.PendingChangesWarning] dialog. */
    fun onLogoutConfirmed() {
        if (_uiState.value.isDeletingAccount) return
        viewModelScope.launch {
            proceedWithLogout()
        }
    }

    /**
     * "Sync now" from the [SettingEventState.PendingChangesWarning] dialog. Fire-and-forget: only
     * schedules a sync pass and keeps the user logged in - it never waits for the result and never
     * logs out afterwards. Offline, WorkManager holds the work until its network constraint is met.
     */
    fun onSyncNowClick() {
        syncScheduler.enqueueSync(SyncTrigger.MANUAL)
    }

    /**
     * Confirmed from the delete-account dialog. The loading flag is set synchronously before
     * launching, so repeated taps can never start a second DELETE /account while one is in flight.
     * Local data is only cleared by [SettingRepository.deleteAccount] after the server confirms.
     */
    fun onDeleteAccountConfirmed() {
        val current = _uiState.value
        if (current.isDeletingAccount || current.isAccountDeleted) return
        _uiState.update { it.copy(isDeletingAccount = true) }

        viewModelScope.launch {
            try {
                when (val result = settingRepository.deleteAccount()) {
                    DeleteAccountResult.Success ->
                        _uiState.update { it.copy(isDeletingAccount = false, isAccountDeleted = true) }

                    // The session is gone (forced logout) - staying on Settings would leave an
                    // unauthenticated user inside the app, so leave through the logout route.
                    DeleteAccountResult.SessionExpired -> {
                        _uiState.update { it.copy(isDeletingAccount = false) }
                        _eventState.emit(SettingEventState.Error(result.toErrorMessage()))
                        _eventState.emit(SettingEventState.LoggedOut)
                    }

                    else -> {
                        _uiState.update { it.copy(isDeletingAccount = false) }
                        _eventState.emit(SettingEventState.Error(result.toErrorMessage()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isDeletingAccount = false) }
                _eventState.emit(SettingEventState.Error("Something went wrong. Please try again."))
            }
        }
    }

    /** Single entry point into the logout operation, shared by both the warned and direct paths. */
    private suspend fun proceedWithLogout() {
        settingRepository.logout()
        _eventState.emit(SettingEventState.LoggedOut)
    }

    private fun DeleteAccountResult.toErrorMessage(): String = when (this) {
        // Deliberately not "was not deleted": a lost response can hide a deletion that succeeded.
        // Retrying is safe - a retry after a hidden success is recognised and completes cleanup.
        DeleteAccountResult.NetworkError ->
            "Couldn't confirm the deletion. Check your connection and try again."
        DeleteAccountResult.SessionExpired ->
            "Your session has expired. Please log in again."
        DeleteAccountResult.ServerError ->
            "The server could not delete your account. Please try again later."
        // AccountNotFound never reaches here: SettingRepository resolves it to another result.
        DeleteAccountResult.AccountNotFound,
        DeleteAccountResult.UnknownError,
        DeleteAccountResult.Success ->
            "Something went wrong. Please try again."
    }
}
