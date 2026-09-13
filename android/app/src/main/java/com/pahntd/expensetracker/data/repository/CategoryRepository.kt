package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.relation.CategoryWithExpenseCount
import com.pahntd.expensetracker.data.remote.api.CategoryApi
import com.pahntd.expensetracker.data.remote.dto.CategoryResponse
import com.pahntd.expensetracker.data.remote.dto.CreateCategoryRequest
import com.pahntd.expensetracker.data.remote.dto.UpdateCategoryRequest
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import javax.inject.Inject

class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val categoryApi: CategoryApi
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

    suspend fun getCategoriesFromApi(): List<CategoryResponse> {
        return categoryApi.getCategories()
    }

    suspend fun createCategoryOnApi(request: CreateCategoryRequest): CategoryResponse {
        return categoryApi.createCategory(request)
    }

    suspend fun updateCategoryOnApi(id: String, request: UpdateCategoryRequest): CategoryResponse {
        return categoryApi.updateCategory(id, request)
    }

    suspend fun deleteCategoryOnApi(id: String): Response<Unit> {
        return categoryApi.deleteCategory(id)
    }

}