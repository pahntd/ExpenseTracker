package com.pahntd.expensetracker.ui.setting

sealed interface SettingEventState {

    data object DeleteAllSuccess : SettingEventState

    /** Pending local changes exist - show the "unsynced changes will be lost" confirmation. */
    data object PendingChangesWarning : SettingEventState

    /** Local logout has finished - return to the existing session/navigation entry point. */
    data object LoggedOut : SettingEventState

    data class Error(
        val message: String
    ) : SettingEventState
}