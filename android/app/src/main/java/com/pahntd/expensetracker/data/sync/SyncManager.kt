package com.pahntd.expensetracker.data.sync

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.network.NetworkMonitor
import com.pahntd.expensetracker.data.network.NetworkState
import com.pahntd.expensetracker.data.remote.api.CategoryApi
import com.pahntd.expensetracker.data.remote.api.TransactionApi
import com.pahntd.expensetracker.data.remote.dto.CategoryResponse
import com.pahntd.expensetracker.data.remote.dto.TransactionResponse
import com.pahntd.expensetracker.data.remote.error.toAppError
import com.pahntd.expensetracker.data.remote.mapper.toCreateRequest
import com.pahntd.expensetracker.data.remote.mapper.toEntity
import com.pahntd.expensetracker.data.remote.mapper.toUpdateRequest
import java.util.concurrent.CancellationException
import javax.inject.Inject

/**
 * Owns sync orchestration between Room and the remote API: Push Create/Update/Delete, guarded
 * against an in-flight request's response overwriting a newer local mutation, followed by
 * [PullManager.pull] to bring Room back up to date with the server's converged state. Every
 * [sync] pass runs Push then Pull, in that order - a push must land (or fail non-retryably)
 * before the fetched snapshot is merged in, so the pull's LWW merge is comparing against Room
 * rows that reflect this pass's own pushes rather than racing them.
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
    private val transactionApi: TransactionApi,
    private val pullManager: PullManager,
    private val syncStatusHolder: SyncStatusHolder,
    private val networkMonitor: NetworkMonitor
) {

    /**
     * Runs one Push pass, then - unless Push hit a [PushResult.RetryableFailure] - one Pull pass,
     * and reduces the two into the single [SyncResult] [SyncWorker] maps onto a WorkManager
     * `Result`:
     *
     * - Push [PushResult.RetryableFailure] -> [SyncResult.Retry] immediately, without pulling.
     *   Retrying the push is the only thing worth doing before the next attempt; pulling a
     *   snapshot now wouldn't change that outcome and just delays the retry.
     * - Push all [PushResult.Success]/[PushResult.NonRetryableFailure] -> proceed to Pull.
     *   A [PushResult.NonRetryableFailure] leaves its row `PENDING_*` (see [pushPhase]) but must
     *   not by itself trigger a WorkManager retry, so this pass still continues to Pull.
     * - Pull [PullResult.RetryableFailure] -> [SyncResult.Retry].
     * - Pull [PullResult.Success] or [PullResult.NonRetryableFailure] -> [SyncResult.Success].
     *   A [PullResult.NonRetryableFailure] (e.g. one unmappable server record) is not chased by
     *   WorkManager's backoff either, and never discards or overwrites local `PENDING_*` rows -
     *   [PullManager.pull] only merges on [PullResult.Success].
     *
     * Alongside [SyncResult] - which only answers "should WorkManager retry" - this also writes
     * the pass's outcome to [syncStatusHolder] as a [SyncState], for UI consumption. The two are
     * deliberately different signals and must not be confused: [SyncResult.Success] does **not**
     * imply [SyncState.SYNCED] - e.g. a [PullResult.NonRetryableFailure] maps to
     * [SyncResult.Success] (nothing for WorkManager to retry) but must still report
     * [SyncState.SYNC_FAILED]/[SyncState.OFFLINE] (nothing was actually synced).
     *
     * If this pass is cancelled (logout, WorkManager stopping the work, process/lifecycle
     * teardown) before reaching one of the returns below, the `catch` resets [syncStatusHolder]
     * to [SyncState.IDLE] and rethrows immediately - cancellation is not a completed pass, so it
     * must never be reported as [SyncState.SYNCED]/[SyncState.SYNC_FAILED]/[SyncState.OFFLINE],
     * and [CancellationException] must never be swallowed. This is a `catch`, not a `finally`:
     * a `finally` would also run on every *normal* return path and clobber the terminal state
     * that was just written a line above it.
     */
    suspend fun sync(trigger: SyncTrigger): SyncResult {
        syncStatusHolder.setState(SyncState.SYNCING)

        try {
            val pushResult = pushPhase()
            if (pushResult is PushResult.RetryableFailure) {
                // SyncResult.Retry unchanged: WorkManager retries this pass. SyncState is a
                // separate decision - nothing succeeded this pass, so it can never be SYNCED here.
                syncStatusHolder.setState(terminalFailureSyncState())
                return SyncResult.Retry
            }

            val pullResult = pullManager.pull()
            val syncResult = when (pullResult) {
                is PullResult.Success -> SyncResult.Success
                PullResult.RetryableFailure -> SyncResult.Retry
                PullResult.NonRetryableFailure -> SyncResult.Success
            }

            // SYNCED requires push AND pull to have both fully succeeded - not just "SyncResult
            // ended up Success". A NonRetryableFailure on either side (or a RetryableFailure on
            // pull) still reports SyncState failure/offline even where SyncResult is Success,
            // since WorkManager not retrying is not the same thing as the pass having actually
            // synced anything.
            val passFullySucceeded = pushResult is PushResult.Success && pullResult is PullResult.Success
            syncStatusHolder.setState(
                if (passFullySucceeded) SyncState.SYNCED else terminalFailureSyncState()
            )

            return syncResult
        } catch (e: CancellationException) {
            syncStatusHolder.setState(SyncState.IDLE)
            throw e
        }
    }

    /**
     * [SyncState.OFFLINE] vs [SyncState.SYNC_FAILED] for a failed/incomplete pass: read the
     * existing [NetworkMonitor] at the moment the terminal state is decided, rather than
     * inferring offline from the failure's [com.pahntd.expensetracker.data.remote.error.AppError]
     * type - every network-flavored exception ([com.pahntd.expensetracker.data.remote.error.AppError.Network])
     * already collapses IOExceptions (timeout, DNS failure, no connectivity, ...) into one case,
     * so it can't reliably distinguish "device is offline" from "server is slow/unreachable."
     */
    private fun terminalFailureSyncState(): SyncState =
        if (networkMonitor.networkState.value == NetworkState.OFFLINE) {
            SyncState.OFFLINE
        } else {
            SyncState.SYNC_FAILED
        }

    /**
     * Runs every push phase and reduces all per-record [PushResult]s down to whether this pass
     * contained at least one [PushResult.RetryableFailure]. Every eligible record is still
     * attempted regardless of what happened to any other record in the same pass; a
     * [PushResult.NonRetryableFailure] here is reported as such (not [PushResult.Success]) purely
     * so [sync] can tell the two apart, even though both let [sync] proceed to Pull.
     */
    private suspend fun pushPhase(): PushResult {
        val categoryCreates = syncCategoryCreates()
        val categoryUpdateResults = syncCategoryUpdates()
        val transactionCreateResults = syncTransactionCreates(categoryCreates.failedIds)
        val transactionUpdateResults = syncTransactionUpdates(categoryCreates.failedIds)
        val transactionDeleteResults = syncTransactionDeletes()
        val categoryDeleteResults = syncCategoryDeletes()

        val allResults = categoryCreates.results + categoryUpdateResults + transactionCreateResults +
            transactionUpdateResults + transactionDeleteResults + categoryDeleteResults

        return when {
            allResults.any { it is PushResult.RetryableFailure } -> PushResult.RetryableFailure
            allResults.any { it is PushResult.NonRetryableFailure } -> PushResult.NonRetryableFailure
            else -> PushResult.Success
        }
    }

    /** Diagnostic only - not used to decide [SyncResult], see [sync]. */
    private suspend fun hasPendingWork(): Boolean {
        return SyncStatus.entries
            .filter { it != SyncStatus.SYNCED }
            .any { status ->
                categoryDao.findBySyncStatus(status).isNotEmpty() ||
                    transactionDao.findBySyncStatus(status).isNotEmpty()
            }
    }

    /** Outcome of pushing every locally pending-create category for one [sync] pass. */
    private data class CategoryCreatePassResult(val results: List<PushResult>, val failedIds: Set<String>)

    /** Outcome of a single category-create push: its retry classification and whether the server is now guaranteed to have it. */
    private data class CreatePushOutcome(val result: PushResult, val confirmedOnServer: Boolean)

    /**
     * Pushes every locally pending-create category. [CategoryCreatePassResult.failedIds] are the
     * ones not guaranteed to exist on the server, so transaction pushes can skip anything that
     * depends on them - regardless of whether the failure was retryable or not, since either way
     * the dependency isn't safely there yet (see [isEligibleForPush]).
     */
    private suspend fun syncCategoryCreates(): CategoryCreatePassResult {
        val pending = categoryDao.findBySyncStatus(SyncStatus.PENDING_CREATE)
        val outcomes = pending.map { category -> category.id to pushCategoryCreate(category) }
        val failedIds = outcomes.filterNot { (_, outcome) -> outcome.confirmedOnServer }
            .map { (id, _) -> id }
            .toSet()
        return CategoryCreatePassResult(outcomes.map { (_, outcome) -> outcome.result }, failedIds)
    }

    private suspend fun syncCategoryUpdates(): List<PushResult> {
        val pending = categoryDao.findBySyncStatus(SyncStatus.PENDING_UPDATE)
        return pending.map { pushCategoryUpdate(it) }
    }

    private suspend fun syncTransactionCreates(failedCategoryCreateIds: Set<String>): List<PushResult> {
        val pending = transactionDao.findBySyncStatus(SyncStatus.PENDING_CREATE)
        return pending.filter { isEligibleForPush(it, failedCategoryCreateIds) }
            .map { pushTransactionCreate(it) }
    }

    private suspend fun syncTransactionUpdates(failedCategoryCreateIds: Set<String>): List<PushResult> {
        val pending = transactionDao.findBySyncStatus(SyncStatus.PENDING_UPDATE)
        return pending.filter { isEligibleForPush(it, failedCategoryCreateIds) }
            .map { pushTransactionUpdate(it) }
    }

    /**
     * A create request must fully succeed and map to an entity, or the local row is left pending;
     * a per-record failure never aborts the rest of the sync pass. [CreatePushOutcome.confirmedOnServer]
     * is true whenever the POST itself succeeded, regardless of whether the response could be
     * written straight into Room (see [applyCategoryCreateResponse]) - it drives dependency
     * skipping, not retry classification.
     */
    private suspend fun pushCategoryCreate(category: CategoryEntity): CreatePushOutcome {
        return try {
            val response = categoryApi.createCategory(category.toCreateRequest())
            applyCategoryCreateResponse(category, response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CreatePushOutcome(e.toAppError().toPushResult(), confirmedOnServer = false)
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
     * pushed as a follow-up PUT - its [PushResult] is propagated as this create's own result, since
     * it's the actual network attempt whose outcome matters for retry purposes. A further mutation
     * racing that PUT is left for the next sync pass rather than chased again here.
     */
    private suspend fun applyCategoryCreateResponse(snapshot: CategoryEntity, response: CategoryResponse): CreatePushOutcome {
        val synced = response.toEntity()
            ?: return CreatePushOutcome(PushResult.NonRetryableFailure, confirmedOnServer = false)
        val responseIsStale = synced.updatedAt < snapshot.updatedAt
        if (!responseIsStale && categoryDao.applyIfUnchanged(synced, snapshot.updatedAt, snapshot.syncStatus)) {
            return CreatePushOutcome(PushResult.Success, confirmedOnServer = true)
        }

        val current = categoryDao.findById(snapshot.id) ?: run {
            val compensateResult = compensateCategoryCreateWithDelete(synced)
            return CreatePushOutcome(compensateResult, confirmedOnServer = compensateResult is PushResult.Success)
        }
        if (current.syncStatus == SyncStatus.PENDING_DELETE) {
            return CreatePushOutcome(PushResult.NonRetryableFailure, confirmedOnServer = false)
        }
        val updateResult = pushCategoryUpdate(current)
        return CreatePushOutcome(updateResult, confirmedOnServer = true)
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
    private suspend fun compensateCategoryCreateWithDelete(serverEntity: CategoryEntity): PushResult {
        return try {
            val response = categoryApi.deleteCategory(serverEntity.id)
            if (response.isSuccessful) return PushResult.Success
            categoryDao.insert(serverEntity.copy(syncStatus = SyncStatus.PENDING_DELETE, deletedAt = System.currentTimeMillis()))
            response.toAppError().toPushResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            categoryDao.insert(serverEntity.copy(syncStatus = SyncStatus.PENDING_DELETE, deletedAt = System.currentTimeMillis()))
            e.toAppError().toPushResult()
        }
    }

    /**
     * A [PushResult.RetryableFailure]/[PushResult.NonRetryableFailure] here is never surfaced by a
     * failed HTTP call alone: when [categoryDao.applyIfUnchanged] declines to write because a newer
     * local edit raced ahead of this response, the push itself still succeeded server-side, so
     * that race is reported as [PushResult.Success] - the freshly-edited row will be picked up as
     * its own pending row on a later pass.
     */
    private suspend fun pushCategoryUpdate(category: CategoryEntity): PushResult {
        return try {
            val response = categoryApi.updateCategory(category.id, category.toUpdateRequest())
            val synced = response.toEntity() ?: return PushResult.NonRetryableFailure
            categoryDao.applyIfUnchanged(synced, category.updatedAt, category.syncStatus)
            PushResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toAppError().toPushResult()
        }
    }

    private suspend fun pushTransactionCreate(transaction: TransactionEntity): PushResult {
        return try {
            val response = transactionApi.createTransaction(transaction.toCreateRequest())
            applyTransactionCreateResponse(transaction, response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toAppError().toPushResult()
        }
    }

    /**
     * Same stale-response, hard-delete-compensation and follow-up-PUT handling as
     * [applyCategoryCreateResponse], for transactions. No `confirmedOnServer` tracking is needed
     * here - nothing in this app depends on a transaction the way transactions depend on categories.
     */
    private suspend fun applyTransactionCreateResponse(snapshot: TransactionEntity, response: TransactionResponse): PushResult {
        val synced = response.toEntity() ?: return PushResult.NonRetryableFailure
        val responseIsStale = synced.updatedAt < snapshot.updatedAt
        if (!responseIsStale && transactionDao.applyIfUnchanged(synced, snapshot.updatedAt, snapshot.syncStatus)) {
            return PushResult.Success
        }

        val current = transactionDao.findById(snapshot.id)
            ?: return compensateTransactionCreateWithDelete(synced)
        if (current.syncStatus == SyncStatus.PENDING_DELETE) return PushResult.NonRetryableFailure
        return pushTransactionUpdate(current)
    }

    /** Same hard-delete compensation as [compensateCategoryCreateWithDelete], for transactions. */
    private suspend fun compensateTransactionCreateWithDelete(serverEntity: TransactionEntity): PushResult {
        return try {
            val response = transactionApi.deleteTransaction(serverEntity.id)
            if (response.isSuccessful) return PushResult.Success
            transactionDao.insert(serverEntity.copy(syncStatus = SyncStatus.PENDING_DELETE, deletedAt = System.currentTimeMillis()))
            response.toAppError().toPushResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            transactionDao.insert(serverEntity.copy(syncStatus = SyncStatus.PENDING_DELETE, deletedAt = System.currentTimeMillis()))
            e.toAppError().toPushResult()
        }
    }

    /** Same race handling as [pushCategoryUpdate], for transactions. */
    private suspend fun pushTransactionUpdate(transaction: TransactionEntity): PushResult {
        return try {
            val response = transactionApi.updateTransaction(transaction.id, transaction.toUpdateRequest())
            val synced = response.toEntity() ?: return PushResult.NonRetryableFailure
            transactionDao.applyIfUnchanged(synced, transaction.updatedAt, transaction.syncStatus)
            PushResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toAppError().toPushResult()
        }
    }

    /** Pushes every locally pending-delete transaction; a failed delete is left pending. */
    private suspend fun syncTransactionDeletes(): List<PushResult> {
        val pending = transactionDao.findBySyncStatus(SyncStatus.PENDING_DELETE)
        return pending.map { pushTransactionDelete(it) }
    }

    /**
     * Pushes every locally pending-delete category that has no remaining transaction dependency.
     * A category is skipped this pass - left `PENDING_DELETE` for a later sync - when a
     * transaction still references it, since that transaction may still exist on the server
     * (its own delete may not have been attempted yet, or may have just failed) and the backend
     * enforces categories via FK + RESTRICT.
     */
    private suspend fun syncCategoryDeletes(): List<PushResult> {
        val pending = categoryDao.findBySyncStatus(SyncStatus.PENDING_DELETE)
        return pending.filterNot { transactionDao.existsByCategory(it.id) }
            .map { pushCategoryDelete(it) }
    }

    private suspend fun pushTransactionDelete(transaction: TransactionEntity): PushResult {
        return try {
            val response = transactionApi.deleteTransaction(transaction.id)
            if (!response.isSuccessful) return response.toAppError().toPushResult()
            transactionDao.deleteById(transaction.id)
            PushResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toAppError().toPushResult()
        }
    }

    private suspend fun pushCategoryDelete(category: CategoryEntity): PushResult {
        return try {
            val response = categoryApi.deleteCategory(category.id)
            if (!response.isSuccessful) return response.toAppError().toPushResult()
            categoryDao.deleteById(category.id)
            PushResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.toAppError().toPushResult()
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
