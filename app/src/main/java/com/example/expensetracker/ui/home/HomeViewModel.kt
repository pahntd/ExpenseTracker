package com.example.expensetracker.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.expensetracker.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {
    init {
        Log.d("Home", "ViewModel created")
    }
}