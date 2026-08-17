package com.pahntd.expensetracker.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticUiState())
    val uiState get() = _uiState.asStateFlow()

    fun loadStatistics() {
        viewModelScope.launch {
            val income = statisticsRepository.getTotalIncome() ?: 0.0
            val expense = statisticsRepository.getTotalExpense() ?: 0.0

            _uiState.value = StatisticUiState(
                totalIncome = income,
                totalExpense = expense,
                balance = income - expense,
                expenseByCategory = statisticsRepository.getExpenseByCategory(),
                incomeByCategory = statisticsRepository.getIncomeByCategory()
            )
        }
    }
}
