package com.pahntd.expensetracker.data.sync

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.remote.api.CategoryApi
import com.pahntd.expensetracker.data.remote.api.TransactionApi
import com.pahntd.expensetracker.data.remote.dto.CategoryResponse
import com.pahntd.expensetracker.data.remote.dto.TransactionResponse
import com.pahntd.expensetracker.data.remote.mapper.toCreateRequest
import com.pahntd.expensetracker.data.remote.mapper.toEntity
import com.pahntd.expensetracker.data.remote.mapper.toUpdateRequest
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Owns sync orchestration between Room and the remote API: Push Create/Update/Delete, guarded
 * against an in-flight request's response overwriting a newer local mutation. Pull/Merge are
 * handled elsewhere.
 *
 * Categories are pushed before transactions because a transaction can reference a category by
 * id, and the server enforces that foreign key. A category whose create fails is not guaranteed
 * to exist on the server, so any transaction referencing it is skipped for this pass rather than
 * pushed against a dependency that may not be there yet.
 *
 * Every response is applied to Room via [CategoryDao.applyIfUnchanged]/[TransactionDao.applyIfUnchanged],
 * which only writes it when the row still matches the exact snapshot (updatedAt + syncStatus) that
 * was sent - so a response for a request that's still in flight when the user edits (or deletes)
 * the same row locally can never clobber that newer mutation. When a CREATE's response can't be
 * applied - either because the row moved on locally, or because the response is an idempotent
 * retry returning an existing server resource that's *older* than what was just sent (the
 * original POST succeeded but its response was lost, and a local edit happened before the retry)
 * - the server already has the resource (the POST itself succeeded, or the retry confirmed it),
 * so the latest local state is immediately followed up with a PUT instead of resurrecting a
 * response that's no longer the current version.
 *
 * A CREATE's row can also be gone entirely: the existing local rule hard-deletes a `PENDING_CREATE`
 * row on delete (the server has never seen it - normally true, but not when a POST for it is still
 * in flight). If that POST then succeeds, the server ends up with a resource the client already
 * considers deleted. That's compensated for by sending a DELETE for it; see
 * [compensateCategoryCreateWithDelete]/[compensateTransactionCreateWithDelete].
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
     * A create request must fully succeed and map to an entity, or the local row is left pending;
     * a per-record failure never aborts the rest of the sync pass. Returns whether the server is
     * now guaranteed to have this category - true whenever the POST itself succeeded, regardless
     * of whether the response could be written straight into Room (see [applyCategoryCreateResponse]).
     */
    private suspend fun pushCategoryCreate(category: CategoryEntity): Boolean {
        return try {
            val response = categoryApi.createCategory(category.toCreateRequest())
            applyCategoryCreateResponse(category, response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Applies a successful create response, guarded against two races:
     *
     * 1. A local mutation happened while the POST was in flight, so the row no longer matches
     *    [snapshot] ([CategoryDao.applyIfUnchanged] itself detects this).
     * 2. The response is *older* than [snapshot] - the server's idempotent-create contract returns
     *    the existing resource rather than erroring, but that existing resource predates a local
     *    edit made before this POST was even sent (e.g. the original POST's response was lost, the
     *    user edited the row, then this retry fired). Applying it would resurrect stale data and
     *    wrongly mark a newer local row `SYNCED`, so it's treated the same as case 1.
     *
     * Either way the POST/retry proves the server already has this id (no new UUID is ever
     * generated here), so rather than dropping the response, the latest local state is immediately
     * pushed as a follow-up PUT. A further mutation racing that PUT is left for the next sync pass
     * rather than chased again here.
     */
    private suspend fun applyCategoryCreateResponse(snapshot: CategoryEntity, response: CategoryResponse): Boolean {
        val synced = response.toEntity() ?: return false
        val responseIsStale = synced.updatedAt < snapshot.updatedAt
        if (!responseIsStale && categoryDao.applyIfUnchanged(synced, snapshot.updatedAt, snapshot.syncStatus)) {
            return true
        }

        val current = categoryDao.findById(snapshot.id)
            ?: return compensateCategoryCreateWithDelete(synced)
        if (current.syncStatus == SyncStatus.PENDING_DELETE) return false
        pushCategoryUpdate(current)
        return true
    }

    /**
     * The local `PENDING_CREATE` row was hard-deleted (the existing "PENDING_CREATE -> delete ->
     * hard delete" rule, normally safe since the server has never seen the row) while this POST
     * was still in flight - but the POST proves [serverEntity]'s id now exists on the server, so a
     * compensating DELETE is sent for it. If that DELETE can't complete right now, a hidden
     * `PENDING_DELETE` tombstone is recreated (same id, `deletedAt` set) so it stays invisible to
     * normal `deletedAt IS NULL` UI queries while letting Part 3's delete phase retry it on a later
     * sync pass - an in-memory failure alone wouldn't survive process death, but this Room row does.
     */
    private suspend fun compensateCategoryCreateWithDelete(serverEntity: CategoryEntity): Boolean {
        return try {
            val response = categoryApi.deleteCategory(serverEntity.id)
            if (response.isSuccessful) return true
            categoryDao.insert(serverEntity.copy(syncStatus = SyncStatus.PENDING_DELETE, deletedAt = System.currentTimeMillis()))
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            categoryDao.insert(serverEntity.copy(syncStatus = SyncStatus.PENDING_DELETE, deletedAt = System.currentTimeMillis()))
            false
        }
    }

    private suspend fun pushCategoryUpdate(category: CategoryEntity): Boolean {
        return try {
            val response = categoryApi.updateCategory(category.id, category.toUpdateRequest())
            val synced = response.toEntity() ?: return false
            categoryDao.applyIfUnchanged(synced, category.updatedAt, category.syncStatus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun pushTransactionCreate(transaction: TransactionEntity): Boolean {
        return try {
            val response = transactionApi.createTransaction(transaction.toCreateRequest())
            applyTransactionCreateResponse(transaction, response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Same stale-response, hard-delete-compensation and follow-up-PUT handling as [applyCategoryCreateResponse], for transactions. */
    private suspend fun applyTransactionCreateResponse(snapshot: TransactionEntity, response: TransactionResponse): Boolean {
        val synced = response.toEntity() ?: return false
        val responseIsStale = synced.updatedAt < snapshot.updatedAt
        if (!responseIsStale && transactionDao.applyIfUnchanged(synced, snapshot.updatedAt, snapshot.syncStatus)) {
            return true
        }

        val current = transactionDao.findById(snapshot.id)
            ?: return compensateTransactionCreateWithDelete(synced)
        if (current.syncStatus == SyncStatus.PENDING_DELETE) return false
        pushTransactionUpdate(current)
        return true
    }

    /** Same hard-delete compensation as [compensateCategoryCreateWithDelete], for transactions. */
    private suspend fun compensateTransactionCreateWithDelete(serverEntity: TransactionEntity): Boolean {
        return try {
            val response = transactionApi.deleteTransaction(serverEntity.id)
            if (response.isSuccessful) return true
            transactionDao.insert(serverEntity.copy(syncStatus = SyncStatus.PENDING_DELETE, deletedAt = System.currentTimeMillis()))
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            transactionDao.insert(serverEntity.copy(syncStatus = SyncStatus.PENDING_DELETE, deletedAt = System.currentTimeMillis()))
            false
        }
    }

    private suspend fun pushTransactionUpdate(transaction: TransactionEntity): Boolean {
        return try {
            val response = transactionApi.updateTransaction(transaction.id, transaction.toUpdateRequest())
            val synced = response.toEntity() ?: return false
            transactionDao.applyIfUnchanged(synced, transaction.updatedAt, transaction.syncStatus)
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
