package com.example.expensetracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExpenseDetailViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val expenseId: Long = checkNotNull(savedStateHandle["expenseId"])

    private val _uiState = MutableStateFlow(ExpenseDetailUiState())
    val uiState: StateFlow<ExpenseDetailUiState> = _uiState.asStateFlow()

    private val _eventState = MutableSharedFlow<ExpenseDetailEventState>()
    val eventState: SharedFlow<ExpenseDetailEventState> = _eventState.asSharedFlow()

    init {
        observeExpense()
    }

    private fun observeExpense() {
        viewModelScope.launch {
            expenseRepository.getExpenseWithCategoryById(expenseId)
                .collect { expense ->
                    _uiState.update {
                        it.copy(
                            expense = expense
                        )
                    }
                }
        }
    }

    fun deleteExpense() {
        viewModelScope.launch {
            expenseRepository.deleteExpenseById(expenseId)
            _eventState.emit(ExpenseDetailEventState.DeleteSuccess)
        }
    }
}