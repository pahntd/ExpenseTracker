package com.pahntd.expensetracker.data.repository

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.converter.TransactionType
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.local.relation.ExpenseWithCategory
import com.pahntd.expensetracker.data.local.sync.SyncStatusPolicy
import com.pahntd.expensetracker.data.remote.api.TransactionApi
import com.pahntd.expensetracker.data.remote.dto.TransactionResponse
import com.pahntd.expensetracker.data.remote.error.AppError
import com.pahntd.expensetracker.data.remote.error.toAppError
import com.pahntd.expensetracker.data.remote.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.CancellationException
import javax.inject.Inject

class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val transactionApi: TransactionApi,
    private val categoryDao: CategoryDao
) {

    fun getAllExpenses(): Flow<List<TransactionEntity>> {
        return transactionDao.getAll()
    }

    fun getAllExpensesWithCategory(): Flow<List<ExpenseWithCategory>> {
        return transactionDao.getAllWithCategory()
    }

    suspend fun getExpenseById(id: String): TransactionEntity? {
        return transactionDao.findById(id)
    }

    fun getExpenseWithCategoryById(id: String): Flow<ExpenseWithCategory?> {
        return transactionDao.getExpenseWithCategoryById(id)
    }

    suspend fun findExpenseWithCategoryById(id: String): ExpenseWithCategory? {
        return transactionDao.findExpenseWithCategoryById(id)
    }

    /**
     * Inserts a new local transaction. Sync metadata is always stamped here rather than trusted
     * from the passed-in [expense], so a freshly created row is always [SyncStatus.PENDING_CREATE]
     * regardless of when/how the caller built the entity.
     */
    suspend fun insertExpense(expense: TransactionEntity) {
        transactionDao.insert(
            expense.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_CREATE,
                deletedAt = null
            )
        )
    }

    /**
     * Updates a transaction's business fields, deriving the correct [SyncStatus] from the
     * persisted record rather than the passed-in [expense]. Returns `false` without writing
     * anything when the row doesn't exist or is pending delete, since a pending-delete row must
     * not be edited through the normal local flow.
     */
    suspend fun updateExpense(expense: TransactionEntity): Boolean {
        val existing = transactionDao.findById(expense.id) ?: return false
        if (!SyncStatusPolicy.canMutate(existing.syncStatus)) return false
        transactionDao.update(
            expense.copy(
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatusPolicy.onLocalUpdate(existing.syncStatus),
                deletedAt = null
            )
        )
        return true
    }

    suspend fun deleteExpense(expense: TransactionEntity) {
        deleteExpenseById(expense.id)
    }

    /**
     * Deletes a transaction by id. A row that was never synced ([SyncStatus.PENDING_CREATE]) is
     * removed outright since the server has never seen it; otherwise it's logically deleted so the
     * pending delete can sync later. No-ops if the row doesn't exist or is already pending delete.
     */
    suspend fun deleteExpenseById(id: String) {
        val existing = transactionDao.findById(id) ?: return
        if (!SyncStatusPolicy.canMutate(existing.syncStatus)) return
        if (SyncStatusPolicy.shouldHardDeleteLocally(existing.syncStatus)) {
            transactionDao.deleteById(id)
        } else {
            val now = System.currentTimeMillis()
            transactionDao.update(
                existing.copy(
                    deletedAt = now,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_DELETE
                )
            )
        }
    }

    fun getExpensesByCategory(categoryId: String): Flow<List<TransactionEntity>> {
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

    /**
     * Pulls transactions from the server and upserts them into Room. Local and server share the
     * same UUID identity, so each transaction's categoryId is reused as-is without needing to
     * resolve it against a separately-generated local id. On failure, the existing local data is
     * left untouched so the Room-backed UI keeps working offline, and the classified [AppError]
     * is returned so the caller can decide what, if anything, to do about it. Returns `null` on
     * success.
     *
     * Every [TransactionResponse] must map cleanly: if any one of them fails to parse, the whole
     * pull fails as [AppError.Unknown] rather than silently dropping the malformed record and
     * upserting the rest, so a bad server record can never partially apply.
     */
    suspend fun pullTransactions(): AppError? {
        val transactions = try {
            getTransactionsFromApi()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return e.toAppError()
        }

        val entities = transactions.map { response ->
            response.toEntity() ?: return AppError.Unknown(
                IllegalStateException("Transaction ${response.id} could not be mapped from the server response")
            )
        }
        transactionDao.upsertAll(entities)
        return null
    }

}
