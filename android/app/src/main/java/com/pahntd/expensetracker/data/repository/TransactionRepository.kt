package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.local.relation.ExpenseWithCategory
import com.pahntd.expensetracker.data.remote.api.TransactionApi
import com.pahntd.expensetracker.data.remote.dto.CreateTransactionRequest
import com.pahntd.expensetracker.data.remote.dto.TransactionResponse
import com.pahntd.expensetracker.data.remote.dto.UpdateTransactionRequest
import kotlinx.coroutines.flow.Flow
import retrofit2.Response
import javax.inject.Inject

class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionApi: TransactionApi
) {

    fun getAllExpenses(): Flow<List<TransactionEntity>> {
        return transactionDao.getAll()
    }

    fun getAllExpensesWithCategory(): Flow<List<ExpenseWithCategory>> {
        return transactionDao.getAllWithCategory()
    }

    suspend fun getExpenseById(id: Long): TransactionEntity? {
        return transactionDao.findById(id)
    }

    fun getExpenseWithCategoryById(id: Long): Flow<ExpenseWithCategory?> {
        return transactionDao.getExpenseWithCategoryById(id)
    }

    suspend fun findExpenseWithCategoryById(id: Long): ExpenseWithCategory? {
        return transactionDao.findExpenseWithCategoryById(id)
    }

    suspend fun insertExpense(expense: TransactionEntity) {
        transactionDao.insert(expense)
    }

    suspend fun updateExpense(expense: TransactionEntity) {
        transactionDao.update(expense)
    }

    suspend fun deleteExpense(expense: TransactionEntity) {
        transactionDao.delete(expense)
    }

    suspend fun deleteExpenseById(id: Long) {
        transactionDao.deleteById(id)
    }

    suspend fun deleteAllExpenses() {
        transactionDao.deleteAll()
    }

    fun getExpensesByCategory(categoryId: Long): Flow<List<TransactionEntity>> {
        return transactionDao.findByCategory(categoryId)
    }

    fun getExpensesByType(type: TransactionType): Flow<List<TransactionEntity>> {
        return transactionDao.findByType(type)
    }

    fun getExpensesBetweenDate(
        startDate: Long,
        endDate: Long
    ): Flow<List<TransactionEntity>> {
        return transactionDao.getBetweenDate(startDate, endDate)
    }

    fun searchExpense(keyword: String): Flow<List<ExpenseWithCategory>> {
        return transactionDao.search(keyword)
    }

    suspend fun getTotalIncome(): Double {
        return transactionDao.getTotalIncome() ?: 0.0
    }

    suspend fun getTotalExpense(): Double {
        return transactionDao.getTotalExpense() ?: 0.0
    }

    suspend fun getBalance(): Double {
        return getTotalIncome() - getTotalExpense()
    }

    suspend fun getTransactionsFromApi(): List<TransactionResponse> {
        return transactionApi.getTransactions()
    }

    suspend fun createTransactionOnApi(request: CreateTransactionRequest): TransactionResponse {
        return transactionApi.createTransaction(request)
    }

    suspend fun updateTransactionOnApi(id: String, request: UpdateTransactionRequest): TransactionResponse {
        return transactionApi.updateTransaction(id, request)
    }

    suspend fun deleteTransactionOnApi(id: String): Response<Unit> {
        return transactionApi.deleteTransaction(id)
    }

}