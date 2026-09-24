package com.pahntd.expensetracker.ui.setting

sealed interface SettingEventState {

    data object DeleteAllSuccess : SettingEventState

    /** Pending local changes exist - show the "unsynced changes will be lost" confirmation. */
    data object PendingChangesWarning : SettingEventState

    data class Error(
        val message: String
    ) : SettingEventState
}