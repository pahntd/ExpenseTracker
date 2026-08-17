package com.pahntd.expensetracker.ui.statistics

import com.pahntd.expensetracker.data.local.dao.ExpenseDao
import com.pahntd.expensetracker.data.local.relation.CategoryWithAmountSummary
import javax.inject.Inject

class StatisticsRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
) {
    suspend fun getTotalIncome(): Double? {
        return expenseDao.getTotalIncome()
    }

    suspend fun getTotalExpense(): Double? {
        return expenseDao.getTotalExpense()
    }

    suspend fun getExpenseByCategory(): List<CategoryWithAmountSummary> {
        return expenseDao.getExpenseByCategory()
    }

    suspend fun getIncomeByCategory(): List<CategoryWithAmountSummary> {
        return expenseDao.getIncomeByCategory()
    }
}