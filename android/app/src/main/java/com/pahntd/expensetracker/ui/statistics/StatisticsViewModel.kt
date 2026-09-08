package com.pahntd.expensetracker.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.local.dao.ExpenseDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val expenseDao: ExpenseDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState get() = _uiState.asStateFlow()

    fun loadStatistics() {
        viewModelScope.launch {
            val income = expenseDao.getTotalIncome() ?: 0.0
            val expense = expenseDao.getTotalExpense() ?: 0.0

            _uiState.value = StatisticsUiState(
                totalIncome = income,
                totalExpense = expense,
                balance = income - expense,
                expenseByCategory = expenseDao.getExpenseByCategory(),
                incomeByCategory = expenseDao.getIncomeByCategory()
            )
        }
    }
}
