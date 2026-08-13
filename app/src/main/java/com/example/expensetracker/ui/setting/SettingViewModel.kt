package com.example.expensetracker.ui.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.DatabaseInitializer
import com.example.expensetracker.data.local.database.ExpenseDatabase
import com.example.expensetracker.repository.CategoryRepository
import com.example.expensetracker.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingViewModel @Inject constructor(
    private val settingRepository: SettingRepository
) : ViewModel() {

    private val _eventState = MutableSharedFlow<SettingEventState>()
    val eventState = _eventState.asSharedFlow()


    fun deleteAllData() {
        viewModelScope.launch {
            try {
                settingRepository.resetAllData()

                _eventState.emit(
                    SettingEventState.DeleteAllSuccess
                )
            } catch (e: Exception) {
                _eventState.emit(
                    SettingEventState.Error(
                        "Failed to delete all data"
                    )
                )
            }
        }
    }

}