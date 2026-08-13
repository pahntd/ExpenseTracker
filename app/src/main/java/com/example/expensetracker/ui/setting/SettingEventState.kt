package com.example.expensetracker.ui.setting

sealed interface SettingEventState {

    data object DeleteAllSuccess : SettingEventState

    data class Error(
        val message: String
    ) : SettingEventState
}