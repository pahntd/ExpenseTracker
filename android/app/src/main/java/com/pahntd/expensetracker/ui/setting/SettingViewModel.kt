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