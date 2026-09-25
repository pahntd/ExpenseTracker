package com.pahntd.expensetracker.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val transactionDao: TransactionDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState get() = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Loads every statistic for [timeFilter]. The date range is recalculated from the current
     * date on each call and passed down to Room, so only matching rows are aggregated;
     * [StatisticTimeFilter.ALL] uses the original unrestricted queries. A previous in-flight load
     * is cancelled so a quick filter switch can never be overwritten by an older result.
     */
    fun loadStatistics(timeFilter: StatisticTimeFilter) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val range = timeFilter.toDateRange(ZonedDateTime.now())
            _uiState.value = if (range == null) {
                loadAllTime(timeFilter)
            } else {
                loadInRange(timeFilter, range)
            }
        }
    }

    private suspend fun loadAllTime(timeFilter: StatisticTimeFilter): StatisticsUiState {
        val income = transactionDao.getTotalIncome() ?: 0.0
        val expense = transactionDao.getTotalExpense() ?: 0.0

        return StatisticsUiState(
            timeFilter = timeFilter,
            totalIncome = income,
            totalExpense = expense,
            balance = income - expense,
            expenseByCategory = transactionDao.getExpenseByCategory(),
            incomeByCategory = transactionDao.getIncomeByCategory()
        )
    }

    private suspend fun loadInRange(
        timeFilter: StatisticTimeFilter,
        range: StatisticDateRange
    ): StatisticsUiState {
        val start = range.startMillis
        val end = range.endMillis
        val income = transactionDao.getTotalIncomeInRange(start, end) ?: 0.0
        val expense = transactionDao.getTotalExpenseInRange(start, end) ?: 0.0

        return StatisticsUiState(
            timeFilter = timeFilter,
            totalIncome = income,
            totalExpense = expense,
            balance = income - expense,
            expenseByCategory = transactionDao.getExpenseByCategoryInRange(start, end),
            incomeByCategory = transactionDao.getIncomeByCategoryInRange(start, end)
        )
    }
}
