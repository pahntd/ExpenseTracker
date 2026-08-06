package com.example.expensetracker.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.local.converter.TransactionType
import com.example.expensetracker.data.local.entity.CategoryEntity
import com.example.expensetracker.data.local.entity.ExpenseEntity
import com.example.expensetracker.repository.CategoryRepository
import com.example.expensetracker.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventState = MutableSharedFlow<AddExpenseEventState>()
    val eventState = _eventState.asSharedFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.update {
                    it.copy(categories = categories)
                }
            }
        }
    }

    fun updateAmount(amount: String) {
        _uiState.update {
            it.copy(amount = amount)
        }
    }

    fun updateType(type: TransactionType) {
        _uiState.update {
            it.copy(type = type)
        }
    }

    fun updateCategory(position : Int) {
        _uiState.update {
            it.copy(selectedCategory = it.categories[position])
        }
    }

    fun updateDate(date: Long) {
        _uiState.update {
            it.copy(date = date)
        }
    }

    fun updateNote(note: String) {
        _uiState.update {
            it.copy(note = note)
        }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            when {

                state.amount.isBlank() -> {
                    _eventState.emit(AddExpenseEventState.Error("Amount is required"))
                    return@launch
                }

                state.selectedCategory == null -> {
                    _eventState.emit(AddExpenseEventState.Error("Category is required"))
                    return@launch
                }

                state.amount.toDoubleOrNull() == null -> {
                    _eventState.emit(AddExpenseEventState.Error("Amount must be a valid number"))
                    return@launch
                }

                state.amount.toDouble() <= 0 -> {
                    _eventState.emit(AddExpenseEventState.Error("Amount must be greater than 0"))
                    return@launch
                }
            }

            val expense = ExpenseEntity(

                amount = state.amount.toDouble(),

                type = state.type,

                categoryId = state.selectedCategory!!.id,

                date = state.date,

                note = state.note
            )
            expenseRepository.insertExpense(expense)
            _eventState.emit(AddExpenseEventState.Success)
        }
    }
}