package com.pahntd.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.relation.CategoryWithExpenseCount
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(category: CategoryEntity): Int

    /**
     * Upserts pulled server categories, matching existing rows by [CategoryEntity.id] since local
     * and server share the same UUID, so re-pulling the same record updates it in place instead
     * of duplicating it.
     */
    @Transaction
    suspend fun upsertAll(categories: List<CategoryEntity>) {
        categories.forEach { category ->
            if (findById(category.id) != null) {
                update(category)
            } else {
                insert(category)
            }
        }
    }

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE name = :name AND deletedAt IS NULL)")
    suspend fun exists(name: String): Boolean

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY name")
    fun getAll(): Flow<List<CategoryEntity>>

    /**
     * Looks up a category regardless of its [CategoryEntity.deletedAt]/[CategoryEntity.syncStatus]
     * state, since mutation flows need to see a pending-delete row to correctly block further
     * edits on it. UI-facing reads should use [getAll] instead.
     */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    /** Reads rows pending push (create/update/delete) to the server, for [com.pahntd.expensetracker.data.sync.SyncManager]. */
    @Query("SELECT * FROM categories WHERE syncStatus = :status")
    suspend fun findBySyncStatus(status: SyncStatus): List<CategoryEntity>

    /**
     * Applies a server response only if the row is still exactly the state
     * ([expectedUpdatedAt]/[expectedSyncStatus]) that was sent to the server, so a response for an
     * in-flight request can never overwrite a newer local mutation that happened while it was
     * running. Returns whether [entity] was applied.
     */
    @Transaction
    suspend fun applyIfUnchanged(
        entity: CategoryEntity,
        expectedUpdatedAt: Long,
        expectedSyncStatus: SyncStatus
    ): Boolean {
        val current = findById(entity.id) ?: return false
        if (current.updatedAt != expectedUpdatedAt || current.syncStatus != expectedSyncStatus) return false
        update(entity)
        return true
    }

    @Query(
        """
    SELECT
        categories.id AS categoryId,
        categories.name AS name,
        categories.icon AS icon,
        COUNT(expenses.id) AS expenseCount
    FROM categories
    LEFT JOIN expenses
        ON categories.id = expenses.categoryId AND expenses.deletedAt IS NULL
    WHERE categories.deletedAt IS NULL
    GROUP BY categories.id
    ORDER BY categories.name ASC
"""
    )
    fun getCategoriesWithExpenseCount(): Flow<List<CategoryWithExpenseCount>>
}
