package com.example.expensetracker.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.local.entity.CategoryEntity
import com.example.expensetracker.data.local.relation.CategoryWithExpenseCount
import com.example.expensetracker.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _eventState = MutableSharedFlow<CategoryEventState>()
    val eventState = _eventState.asSharedFlow()

    val categories: StateFlow<List<CategoryWithExpenseCount>> =
        categoryRepository.getCategoriesWithCount().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun addCategory(category: CategoryEntity) {
        viewModelScope.launch {
            val result = categoryRepository.insertCategory(
                category.copy(
                    name = normalizeCategoryName(category.name)
                )
            )
            if (result == -1L) {
                _eventState.emit(
                    CategoryEventState.Error("Category already exists !")
                )
                return@launch
            }
            _eventState.emit(CategoryEventState.CategoryAdded)
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch {
            val result = categoryRepository.updateCategory(
                category.copy(
                    name = normalizeCategoryName(category.name)
                )
            )
            if (result <= 0) {
                _eventState.emit(
                    CategoryEventState.Error("Category already exists !")
                )
                return@launch
            }
            _eventState.emit(CategoryEventState.CategoryAdded)
        }
    }

    fun deleteCategoryById(id: Long) {
        viewModelScope.launch {
            categoryRepository.deleteById(id)
        }
    }

    private fun normalizeCategoryName(name: String): String {
        return name.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase().replaceFirstChar { it.uppercaseChar() }
    }

}