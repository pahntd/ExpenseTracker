package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.relation.CategoryWithExpenseCount
import com.pahntd.expensetracker.data.local.sync.SyncStatusPolicy
import com.pahntd.expensetracker.data.remote.api.CategoryApi
import com.pahntd.expensetracker.data.remote.dto.CategoryResponse
import com.pahntd.expensetracker.data.remote.error.AppError
import com.pahntd.expensetracker.data.remote.error.toAppError
import com.pahntd.expensetracker.data.remote.mapper.toEntity
import kotlinx.coroutines.flow.Flow
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

    /**
     * Inserts a new local category. Sync metadata is always stamped here rather than trusted from
     * the passed-in [category], so a freshly created row is always [SyncStatus.PENDING_CREATE]
     * regardless of when/how the caller built the entity.
     */
    suspend fun insertCategory(category: CategoryEntity): Long {
        return categoryDao.insert(
            category.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_CREATE,
                deletedAt = null
            )
        )
    }

    /**
     * Updates a category's business fields, deriving the correct [SyncStatus] from the persisted
     * record rather than the passed-in [category]. Returns 0 (no rows affected) when the category
     * doesn't exist, is a default category, or is pending delete - all cases where a local edit
     * must not go through.
     */
    suspend fun updateCategory(category: CategoryEntity): Int {
        val existing = categoryDao.findById(category.id) ?: return 0
        if (existing.isDefault) return 0
        if (!SyncStatusPolicy.canMutate(existing.syncStatus)) return 0
        return categoryDao.update(
            existing.copy(
                name = category.name,
                icon = category.icon,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatusPolicy.onLocalUpdate(existing.syncStatus)
            )
        )
    }

    /**
     * Deletes a category by id, unless it's a default category or already pending delete. A row
     * that was never synced ([SyncStatus.PENDING_CREATE]) is removed outright since the server has
     * never seen it; otherwise it's logically deleted so the pending delete can sync later.
     */
    suspend fun deleteById(id: String) {
        val existing = categoryDao.findById(id) ?: return
        if (existing.isDefault) return
        if (!SyncStatusPolicy.canMutate(existing.syncStatus)) return
        if (SyncStatusPolicy.shouldHardDeleteLocally(existing.syncStatus)) {
            categoryDao.deleteById(id)
        } else {
            val now = System.currentTimeMillis()
            categoryDao.update(
                existing.copy(
                    deletedAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_DELETE
                )
            )
        }
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