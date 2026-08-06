package com.example.expensetracker.ui.add

import com.example.expensetracker.data.local.converter.TransactionType
import com.example.expensetracker.data.local.entity.CategoryEntity

data class AddExpenseUiState(

    val amount: String = "",

    val type: TransactionType = TransactionType.EXPENSE,

    val categories: List<CategoryEntity> = emptyList(),

    val selectedCategory: CategoryEntity? = null,

    val date: Long = System.currentTimeMillis(),

    val note: String = ""
)
