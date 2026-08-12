package com.example.expensetracker.ui.category

sealed interface CategoryEventState {

    data class Error(val message: String) : CategoryEventState

    data object CategoryAdded : CategoryEventState

}