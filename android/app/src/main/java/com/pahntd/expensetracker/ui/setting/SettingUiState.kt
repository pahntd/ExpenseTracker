package com.pahntd.expensetracker.ui.setting

data class SettingUiState(
    /** DELETE /account is in flight - destructive actions stay disabled until it settles. */
    val isDeletingAccount: Boolean = false,
    /**
     * The server confirmed the deletion and local cleanup has finished. Kept as state rather than
     * a one-off event so the navigation to Login still happens if the screen was stopped when the
     * result arrived.
     */
    val isAccountDeleted: Boolean = false
)
