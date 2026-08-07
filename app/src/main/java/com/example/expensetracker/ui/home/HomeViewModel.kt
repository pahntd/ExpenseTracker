package com.example.expensetracker.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.local.converter.TransactionType
import com.example.expensetracker.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeTransactions()
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            expenseRepository.getAllExpensesWithCategory().collect { list ->
                val income = list.filter { it.expense.type == TransactionType.INCOME }
                    .sumOf { it.expense.amount }
                val expense = list.filter { it.expense.type == TransactionType.EXPENSE }
                    .sumOf { it.expense.amount }
                _uiState.update {
                    it.copy(
                        transactions = list,
                        totalIncome = income,
                        totalExpense = expense,
                        balance = income - expense
                    )
                }
            }
        }
    }

}