package com.pahntd.expensetracker.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import com.pahntd.expensetracker.data.local.DefaultCategories
import com.pahntd.expensetracker.data.local.converter.TransactionTypeConverter
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.ExpenseDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.ExpenseEntity

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