package com.pahntd.expensetracker.di

import android.content.Context
import androidx.room.Room
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.ExpenseDao
import com.pahntd.expensetracker.data.local.database.ExpenseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExpenseDatabase {
        return Room.databaseBuilder(
            context,
            ExpenseDatabase::class.java,
            "expense_database"
        ).build()
    }

    @Provides
    fun provideExpenseDao(
        database: ExpenseDatabase
    ): ExpenseDao {
        return database.expenseDao()
    }

    @Provides
    fun provideCategoryDao(
        database: ExpenseDatabase
    ): CategoryDao {
        return database.categoryDao()
    }

}