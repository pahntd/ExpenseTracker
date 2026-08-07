package com.example.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.expensetracker.data.local.converter.TransactionType
import com.example.expensetracker.data.local.entity.ExpenseEntity
import com.example.expensetracker.data.local.relation.ExpenseWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun findById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE categoryId = :categoryId ORDER BY date DESC")
    fun findByCategory(categoryId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE type = :type ORDER BY date DESC")
    fun findByType(type: TransactionType): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE note LIKE '%' || :keyword || '%'
        ORDER BY date DESC
    """
    )
    fun search(keyword: String): Flow<List<ExpenseEntity>>

    @Query(
        """
    SELECT * FROM expenses
    WHERE date BETWEEN :startDate AND :endDate
    ORDER BY date DESC
"""
    )
    fun getBetweenDate(
        startDate: Long,
        endDate: Long
    ): Flow<List<ExpenseEntity>>

    @Query(
        """
    SELECT SUM(amount)
    FROM expenses
    WHERE type = 'INCOME'
"""
    )
    suspend fun getTotalIncome(): Double?

    @Query(
        """
    SELECT SUM(amount)
    FROM expenses
    WHERE type = 'EXPENSE'
"""
    )
    suspend fun getTotalExpense(): Double?

    @Transaction
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllWithCategory(): Flow<List<ExpenseWithCategory>>

}