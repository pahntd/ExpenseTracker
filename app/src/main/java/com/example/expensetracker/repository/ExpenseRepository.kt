package com.example.expensetracker.repository

import com.example.expensetracker.data.local.converter.TransactionType
import com.example.expensetracker.data.local.dao.ExpenseDao
import com.example.expensetracker.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {

    fun getAllExpenses(): Flow<List<ExpenseEntity>> {
        return expenseDao.getAll()
    }

    suspend fun getExpenseById(id: Long): ExpenseEntity? {
        return expenseDao.findById(id)
    }

    suspend fun insertExpense(expense: ExpenseEntity) {
        expenseDao.insert(expense)
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        expenseDao.update(expense)
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.delete(expense)
    }

    suspend fun deleteExpenseById(id: Long) {
        expenseDao.deleteById(id)
    }

    suspend fun deleteAllExpenses() {
        expenseDao.deleteAll()
    }

    fun getExpensesByCategory(categoryId: Long): Flow<List<ExpenseEntity>> {
        return expenseDao.findByCategory(categoryId)
    }

    fun getExpensesByType(type: TransactionType): Flow<List<ExpenseEntity>> {
        return expenseDao.findByType(type)
    }

    fun getExpensesBetweenDate(
        startDate: Long,
        endDate: Long
    ): Flow<List<ExpenseEntity>> {
        return expenseDao.getBetweenDate(startDate, endDate)
    }

    fun searchExpense(keyword: String): Flow<List<ExpenseEntity>> {
        return expenseDao.search(keyword)
    }

    suspend fun getTotalIncome(): Double {
        return expenseDao.getTotalIncome() ?: 0.0
    }

    suspend fun getTotalExpense(): Double {
        return expenseDao.getTotalExpense() ?: 0.0
    }

    suspend fun getBalance(): Double {
        return getTotalIncome() - getTotalExpense()
    }

}