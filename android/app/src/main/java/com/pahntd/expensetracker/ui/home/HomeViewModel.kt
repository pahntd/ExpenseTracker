package com.pahntd.expensetracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val searchQuery = MutableStateFlow("")

    init {
        observeSummary()
        observeSearchQuery()
    }

    private fun observeSummary() {
        viewModelScope.launch {
            transactionRepository.getAllExpensesWithCategory().collect { list ->
                val income = list.filter { it.transaction.type == TransactionType.INCOME }
                    .sumOf { it.transaction.amount }
                val expense = list.filter { it.transaction.type == TransactionType.EXPENSE }
                    .sumOf { it.transaction.amount }
                _uiState.update {
                    it.copy(
                        totalIncome = income,
                        totalExpense = expense,
                        balance = income - expense
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            searchQuery.flatMapLatest { keyword ->
                if (keyword.isBlank()) {
                    transactionRepository.getAllExpensesWithCategory()
                } else {
                    transactionRepository.searchExpense(keyword)
                }
            }.collect { list ->
                _uiState.update {
                    it.copy(transactions = list)
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }
}
