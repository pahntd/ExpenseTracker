package com.example.expensetracker.ui.setting

import com.example.expensetracker.data.local.database.ExpenseDatabase
import javax.inject.Inject

class SettingRepository @Inject constructor(
    private val database: ExpenseDatabase
) {
    suspend fun resetAllData() {
        database.resetAllData()
    }
}