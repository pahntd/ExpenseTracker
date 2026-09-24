package com.pahntd.expensetracker.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.repository.SettingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository
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

    /** Single entry point into the logout operation, shared by both the warned and direct paths. */
    private suspend fun proceedWithLogout() {
        settingRepository.logout()
        _eventState.emit(SettingEventState.LoggedOut)
    }

}