package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.relation.CategoryWithExpenseCount
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {

    fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getAll()
    }

    suspend fun getCategoryById(id: Long): CategoryEntity? {
        return categoryDao.findById(id)
    }

    suspend fun findByName(name: String): CategoryEntity? {
        return categoryDao.findByName(name)
    }

    suspend fun insertCategory(category: CategoryEntity): Long {
        return categoryDao.insert(category)
    }

    suspend fun updateCategory(category: CategoryEntity): Int {
        return categoryDao.update(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        categoryDao.delete(category)
    }

    suspend fun deleteAllCategories() {
        categoryDao.deleteAll()
    }

    suspend fun deleteById(id: Long) {
        categoryDao.deleteById(id)
    }

    suspend fun countCategories(): Int {
        return categoryDao.count()
    }

    fun getCategoriesWithCount(): Flow<List<CategoryWithExpenseCount>> {
        return categoryDao.getCategoriesWithExpenseCount()
    }

}