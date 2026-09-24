package com.pahntd.expensetracker.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.repository.SettingRepository
import com.pahntd.expensetracker.data.sync.SyncScheduler
import com.pahntd.expensetracker.data.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _eventState = MutableSharedFlow<SettingEventState>()
    val eventState = _eventState.asSharedFlow()

    /** Tapped from Settings: warn first when there are unsynced local changes, else log out directly. */
    fun onLogoutClick() {
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

    /** Single entry point into the logout operation, shared by both the warned and direct paths. */
    private suspend fun proceedWithLogout() {
        settingRepository.logout()
        _eventState.emit(SettingEventState.LoggedOut)
    }

}