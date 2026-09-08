package com.pahntd.expensetracker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.pahntd.expensetracker.utils.AppPreferences
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ExpenseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        applyTheme()
    }

    private fun applyTheme() {
        val preferences = getSharedPreferences(
            AppPreferences.PREF_NAME,
            MODE_PRIVATE
        )
        val isDarkMode = preferences.getBoolean(
            AppPreferences.KEY_DARK_MODE, false
        )
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}