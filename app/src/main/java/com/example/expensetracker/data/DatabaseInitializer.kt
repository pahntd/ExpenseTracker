package com.example.expensetracker.data

import com.example.expensetracker.data.local.DefaultCategories
import com.example.expensetracker.data.local.dao.CategoryDao
import javax.inject.Inject

class DatabaseInitializer @Inject constructor(
    private val categoryDao: CategoryDao
) {
    suspend fun seed() {
        if (categoryDao.count() > 0) return
        categoryDao.insertAll(DefaultCategories.categories)
    }
}