package com.pahntd.expensetracker.ui.add

import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.entity.CategoryEntity

data class AddExpenseUiState(

    val amount: String = "",

    val type: TransactionType = TransactionType.EXPENSE,

    val categories: List<CategoryEntity> = emptyList(),

    val selectedCategory: CategoryEntity? = null,

    val date: Long = System.currentTimeMillis(),

    val note: String = ""
)
