package com.pahntd.expensetracker.data

import com.pahntd.expensetracker.data.local.DefaultCategories
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import javax.inject.Inject

class DatabaseInitializer @Inject constructor(
    private val categoryDao: CategoryDao
) {
    suspend fun seed() {
        if (categoryDao.count() > 0) return
        categoryDao.insertAll(DefaultCategories.categories)
    }
}