package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.dao.ExpenseDao
import com.pahntd.expensetracker.data.local.entity.ExpenseEntity
import com.pahntd.expensetracker.data.local.relation.ExpenseWithCategory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExpenseRepository @Inject constructor(
    private val expenseDao: ExpenseDao
) {

    fun getAllExpenses(): Flow<List<ExpenseEntity>> {
        return expenseDao.getAll()
    }

    fun getAllExpensesWithCategory(): Flow<List<ExpenseWithCategory>> {
        return expenseDao.getAllWithCategory()
    }

    suspend fun getExpenseById(id: Long): ExpenseEntity? {
        return expenseDao.findById(id)
    }

    fun getExpenseWithCategoryById(id: Long): Flow<ExpenseWithCategory?> {
        return expenseDao.getExpenseWithCategoryById(id)
    }

    suspend fun findExpenseWithCategoryById(id: Long): ExpenseWithCategory? {
        return expenseDao.findExpenseWithCategoryById(id)
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

    fun searchExpense(keyword: String): Flow<List<ExpenseWithCategory>> {
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