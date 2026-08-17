package com.pahntd.expensetracker.ui.add

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.ExpenseEntity
import com.pahntd.expensetracker.data.repository.CategoryRepository
import com.pahntd.expensetracker.data.repository.ExpenseRepository
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
    private val expenseRepository: ExpenseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventState = MutableSharedFlow<AddExpenseEventState>()
    val eventState = _eventState.asSharedFlow()

    private val expenseId: Long =
        savedStateHandle["expenseId"] ?: -1L

    private val isEditMode: Boolean
        get() = expenseId != -1L

    init {
        loadCategories()
        if (isEditMode) loadExpenseEditMode()
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

    private fun loadExpenseEditMode() {
        viewModelScope.launch {
            val expenseWithCategory =
                expenseRepository.findExpenseWithCategoryById(expenseId) ?: return@launch
            _uiState.update {
                it.copy(
                    amount = expenseWithCategory.expense.amount.toString(),
                    type = expenseWithCategory.expense.type,
                    selectedCategory = expenseWithCategory.category,
                    date = expenseWithCategory.expense.date,
                    note = expenseWithCategory.expense.title.orEmpty()
                )
            }
            _eventState.emit(
                AddExpenseEventState.EditDataLoaded(expenseWithCategory)
            )
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

    fun updateCategory(position: Int) {
        _uiState.update {
            it.copy(selectedCategory = it.categories[position])
        }
    }

    fun updateCategory(category: CategoryEntity) {
        _uiState.update {
            it.copy(selectedCategory = category)
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

                title = state.note
            )
            if (isEditMode) {
                expenseRepository.updateExpense(expense.copy(id = expenseId))
            } else {
                expenseRepository.insertExpense(expense)
            }
            _eventState.emit(AddExpenseEventState.Success)
        }
    }
}