package com.example.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.expensetracker.data.local.entity.CategoryEntity
import com.example.expensetracker.data.local.relation.CategoryWithExpenseCount
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM categories WHERE name = :name)")
    suspend fun exists(name: String): Boolean

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM categories ORDER BY name")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): CategoryEntity?

    @Query(
        """
    SELECT 
        categories.id AS categoryId,
        categories.name AS name,
        categories.icon AS icon,
        COUNT(expenses.id) AS expenseCount
    FROM categories
    LEFT JOIN expenses
        ON categories.id = expenses.categoryId
    GROUP BY categories.id
    ORDER BY categories.name ASC
"""
    )
    fun getCategoriesWithExpenseCount(): Flow<List<CategoryWithExpenseCount>>
}