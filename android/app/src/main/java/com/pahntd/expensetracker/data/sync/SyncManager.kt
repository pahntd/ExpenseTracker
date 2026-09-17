package com.pahntd.expensetracker.data.sync

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.remote.api.CategoryApi
import com.pahntd.expensetracker.data.remote.api.TransactionApi
import com.pahntd.expensetracker.data.remote.mapper.toCreateRequest
import com.pahntd.expensetracker.data.remote.mapper.toEntity
import com.pahntd.expensetracker.data.remote.mapper.toUpdateRequest
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Owns sync orchestration between Room and the remote API. This part implements Push
 * Create/Update only - Delete, Pull/Merge and concurrency protection are handled elsewhere.
 *
 * Categories are pushed before transactions because a transaction can reference a category by
 * id, and the server enforces that foreign key. A category whose create fails is not guaranteed
 * to exist on the server, so any transaction referencing it is skipped for this pass rather than
 * pushed against a dependency that may not be there yet.
 */
class SyncManager @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val categoryApi: CategoryApi,
    private val transactionApi: TransactionApi
) {

    suspend fun sync(trigger: SyncTrigger) {
        val failedCategoryCreateIds = syncCategoryCreates()
        syncCategoryUpdates()
        syncTransactionCreates(failedCategoryCreateIds)
        syncTransactionUpdates(failedCategoryCreateIds)
        syncTransactionDeletes()
        syncCategoryDeletes()
    }

    /**
     * Pushes every locally pending-create category. Returns the ids of the ones that failed to
     * sync, i.e. are still not guaranteed to exist on the server, so transaction pushes can skip
     * anything that depends on them.
     */
    private suspend fun syncCategoryCreates(): Set<String> {
        val pending = categoryDao.findBySyncStatus(SyncStatus.PENDING_CREATE)
        return pending.filterNot { pushCategoryCreate(it) }.map { it.id }.toSet()
    }

    private suspend fun syncCategoryUpdates() {
        val pending = categoryDao.findBySyncStatus(SyncStatus.PENDING_UPDATE)
        pending.forEach { pushCategoryUpdate(it) }
    }

    private suspend fun syncTransactionCreates(failedCategoryCreateIds: Set<String>) {
        val pending = transactionDao.findBySyncStatus(SyncStatus.PENDING_CREATE)
        pending.filter { isEligibleForPush(it, failedCategoryCreateIds) }
            .forEach { pushTransactionCreate(it) }
    }

    private suspend fun syncTransactionUpdates(failedCategoryCreateIds: Set<String>) {
        val pending = transactionDao.findBySyncStatus(SyncStatus.PENDING_UPDATE)
        pending.filter { isEligibleForPush(it, failedCategoryCreateIds) }
            .forEach { pushTransactionUpdate(it) }
    }

    /**
     * A create/update request must fully succeed and map to an entity, or the local row is left
     * pending; a per-record failure never aborts the rest of the sync pass.
     */
    private suspend fun pushCategoryCreate(category: CategoryEntity): Boolean {
        return try {
            val response = categoryApi.createCategory(category.toCreateRequest())
            val synced = response.toEntity() ?: return false
            categoryDao.update(synced)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pushCategoryUpdate(category: CategoryEntity): Boolean {
        return try {
            val response = categoryApi.updateCategory(category.id, category.toUpdateRequest())
            val synced = response.toEntity() ?: return false
            categoryDao.update(synced)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pushTransactionCreate(transaction: TransactionEntity): Boolean {
        return try {
            val response = transactionApi.createTransaction(transaction.toCreateRequest())
            val synced = response.toEntity() ?: return false
            transactionDao.update(synced)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pushTransactionUpdate(transaction: TransactionEntity): Boolean {
        return try {
            val response = transactionApi.updateTransaction(transaction.id, transaction.toUpdateRequest())
            val synced = response.toEntity() ?: return false
            transactionDao.update(synced)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Pushes every locally pending-delete transaction; a failed delete is left pending. */
    private suspend fun syncTransactionDeletes() {
        val pending = transactionDao.findBySyncStatus(SyncStatus.PENDING_DELETE)
        pending.forEach { pushTransactionDelete(it) }
    }

    /**
     * Pushes every locally pending-delete category that has no remaining transaction dependency.
     * A category is skipped this pass - left `PENDING_DELETE` for a later sync - when a
     * transaction still references it, since that transaction may still exist on the server
     * (its own delete may not have been attempted yet, or may have just failed) and the backend
     * enforces categories via FK + RESTRICT.
     */
    private suspend fun syncCategoryDeletes() {
        val pending = categoryDao.findBySyncStatus(SyncStatus.PENDING_DELETE)
        pending.filterNot { transactionDao.existsByCategory(it.id) }
            .forEach { pushCategoryDelete(it) }
    }

    private suspend fun pushTransactionDelete(transaction: TransactionEntity): Boolean {
        return try {
            val response = transactionApi.deleteTransaction(transaction.id)
            if (!response.isSuccessful) return false
            transactionDao.deleteById(transaction.id)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pushCategoryDelete(category: CategoryEntity): Boolean {
        return try {
            val response = categoryApi.deleteCategory(category.id)
            if (!response.isSuccessful) return false
            categoryDao.deleteById(category.id)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * A transaction is safe to push only when it doesn't reference a category that is still not
 * guaranteed to exist on the server ([failedCategoryCreateIds]). Kept as a standalone pure function
 * so the dependency decision can be unit tested without a DAO/API.
 */
internal fun isEligibleForPush(transaction: TransactionEntity, failedCategoryCreateIds: Set<String>): Boolean {
    val categoryId = transaction.categoryId ?: return true
    return categoryId !in failedCategoryCreateIds
}
