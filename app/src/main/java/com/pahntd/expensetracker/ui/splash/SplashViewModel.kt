package com.pahntd.expensetracker.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.DatabaseInitializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val initializer: DatabaseInitializer
) : ViewModel() {
    fun initialize(onFinished: () -> Unit) {
        viewModelScope.launch {
            initializer.seed()
            onFinished()
        }
    }
}