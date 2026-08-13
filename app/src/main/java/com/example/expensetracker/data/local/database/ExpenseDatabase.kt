package com.example.expensetracker.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import com.example.expensetracker.data.local.DefaultCategories
import com.example.expensetracker.data.local.converter.TransactionTypeConverter
import com.example.expensetracker.data.local.dao.CategoryDao
import com.example.expensetracker.data.local.dao.ExpenseDao
import com.example.expensetracker.data.local.entity.CategoryEntity
import com.example.expensetracker.data.local.entity.ExpenseEntity

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(TransactionTypeConverter::class)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao

    @Transaction
    open suspend fun resetAllData() {
        expenseDao().deleteAll()
        categoryDao().deleteAll()
        categoryDao().insertAll(
            DefaultCategories.categories
        )
    }
}