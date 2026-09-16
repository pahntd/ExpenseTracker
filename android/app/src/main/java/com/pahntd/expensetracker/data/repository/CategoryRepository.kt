package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.relation.CategoryWithExpenseCount
import com.pahntd.expensetracker.data.remote.api.CategoryApi
import com.pahntd.expensetracker.data.remote.dto.CategoryResponse
import com.pahntd.expensetracker.data.remote.dto.CreateCategoryRequest
import com.pahntd.expensetracker.data.remote.dto.UpdateCategoryRequest
import com.pahntd.expensetracker.data.remote.error.AppError
import com.pahntd.expensetracker.data.remote.error.toAppError
import com.pahntd.expensetracker.data.remote.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import java.util.concurrent.CancellationException
import javax.inject.Inject

class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val categoryApi: CategoryApi
) {

    fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getAll()
    }

    suspend fun getCategoryById(id: String): CategoryEntity? {
        return categoryDao.findById(id)
    }

    suspend fun findByName(name: String): CategoryEntity? {
        return categoryDao.findByName(name)
    }

    suspend fun insertCategory(category: CategoryEntity): Long {
        return categoryDao.insert(category)
    }

    /**
     * Updates a category, unless the persisted record is a default category. The persisted
     * record's [CategoryEntity.isDefault] is authoritative rather than the passed-in [category],
     * since callers may build a partial entity that doesn't carry the real flag. Returns 0 when
     * the category is default or doesn't exist, matching the "no rows affected" contract already
     * used for a naming conflict.
     */
    suspend fun updateCategory(category: CategoryEntity): Int {
        val existing = categoryDao.findById(category.id) ?: return 0
        if (existing.isDefault) return 0
        return categoryDao.update(category)
    }

    /** Deletes a category, unless the persisted record is a default category. */
    suspend fun deleteCategory(category: CategoryEntity) {
        val existing = categoryDao.findById(category.id) ?: return
        if (existing.isDefault) return
        categoryDao.delete(category)
    }

    suspend fun deleteAllCategories() {
        categoryDao.deleteAll()
    }

    /** Deletes a category by id, unless the persisted record is a default category. */
    suspend fun deleteById(id: String) {
        val existing = categoryDao.findById(id) ?: return
        if (existing.isDefault) return
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

    /**
     * Pulls categories from the server and upserts them into Room. On failure, the existing
     * local data is left untouched so the Room-backed UI keeps working offline, and the
     * classified [AppError] is returned so the caller can decide what, if anything, to do about
     * it. Returns `null` on success.
     *
     * Every [CategoryResponse] must map cleanly: if any one of them fails to parse, the whole
     * pull fails as [AppError.Unknown] rather than silently dropping the malformed record and
     * upserting the rest, so a bad server record can never partially apply.
     */
    suspend fun pullCategories(): AppError? {
        val categories = try {
            getCategoriesFromApi()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return e.toAppError()
        }

        val entities = categories.map { response ->
            response.toEntity() ?: return AppError.Unknown(
                IllegalStateException("Category ${response.id} could not be mapped from the server response")
            )
        }
        categoryDao.upsertAll(entities)
        return null
    }

}