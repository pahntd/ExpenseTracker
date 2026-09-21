package com.pahntd.expensetracker.data.sync

import androidx.room.withTransaction
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import com.pahntd.expensetracker.data.local.database.ExpenseDatabase
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.TransactionEntity
import com.pahntd.expensetracker.data.remote.api.CategoryApi
import com.pahntd.expensetracker.data.remote.api.TransactionApi
import com.pahntd.expensetracker.data.remote.error.toAppError
import com.pahntd.expensetracker.data.remote.mapper.toEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Fetches one complete server snapshot - every category and every transaction - and merges it
 * into Room per entity (see [decideMergeAction] for the rules), so pulled server state can never
 * clobber an unsynced local change or resurrect something the user deleted. Categories and
 * transactions are fetched concurrently since neither depends on the other's response; either
 * request failing, or any single item failing to map, invalidates the whole snapshot - nothing is
 * merged or persisted unless both resources come back valid (there is no partial
 * [PullResult.Success] and no partial merge).
 *
 * Not called from [SyncManager] yet - orchestrating push and pull together is separate work.
 */
class PullManager @Inject constructor(
    private val categoryApi: CategoryApi,
    private val transactionApi: TransactionApi,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val database: ExpenseDatabase
) {

    /**
     * Fetches the server snapshot and, only on [PullResult.Success], merges and persists it. The
     * merge runs inside a single Room transaction ([mergeSnapshot]) so a crash or cancellation
     * partway through can never leave categories and transactions reconciled against different
     * points in time.
     */
    suspend fun pull(): PullResult {
        val snapshot = fetchSnapshot()

        if (snapshot is PullResult.Success) {
            persistSnapshot(snapshot)
        }

        return snapshot
    }

    private suspend fun persistSnapshot(snapshot: PullResult.Success) {
        database.withTransaction {
            mergeSnapshot(
                categoryDao = categoryDao,
                transactionDao = transactionDao,
                serverCategories = snapshot.categories,
                serverTransactions = snapshot.transactions
            )
        }
    }

    /**
     * The fetch-and-map half of [pull], kept separate (and internal) so it can be exercised
     * without touching Room: it has no persistence side effects of its own.
     */
    internal suspend fun fetchSnapshot(): PullResult = coroutineScope {
        val categoriesDeferred = async { fetchCategories() }
        val transactionsDeferred = async { fetchTransactions() }
        combine(categoriesDeferred.await(), transactionsDeferred.await())
    }

    /**
     * Combines both fetches into a single all-or-nothing [PullResult]. When both failed, and
     * differently, [PullResult.NonRetryableFailure] wins over [PullResult.RetryableFailure] -
     * retrying would only ever fix the retryable half, while the non-retryable half (a rejected
     * request, or a server record that can never be mapped) would keep failing forever.
     */
    private fun combine(
        categories: FetchOutcome<List<CategoryEntity>>,
        transactions: FetchOutcome<List<TransactionEntity>>
    ): PullResult {
        val failures = listOfNotNull(
            (categories as? FetchOutcome.Failure)?.result,
            (transactions as? FetchOutcome.Failure)?.result
        )
        return when {
            failures.any { it == PullResult.NonRetryableFailure } -> PullResult.NonRetryableFailure
            failures.isNotEmpty() -> PullResult.RetryableFailure
            else -> PullResult.Success(
                categories = (categories as FetchOutcome.Success).value,
                transactions = (transactions as FetchOutcome.Success).value
            )
        }
    }

    /**
     * Every [com.pahntd.expensetracker.data.remote.dto.CategoryResponse] in the payload must map
     * cleanly, or the whole fetch is [PullResult.NonRetryableFailure] - a malformed record is
     * never silently dropped (no `mapNotNull`) while the rest of the snapshot is kept.
     */
    private suspend fun fetchCategories(): FetchOutcome<List<CategoryEntity>> {
        val responses = try {
            categoryApi.getCategories()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FetchOutcome.Failure(e.toAppError().toPullResult())
        }

        val entities = responses.map { response ->
            response.toEntity() ?: return FetchOutcome.Failure(PullResult.NonRetryableFailure)
        }
        return FetchOutcome.Success(entities)
    }

    /** Same all-or-nothing mapping contract as [fetchCategories], for transactions. */
    private suspend fun fetchTransactions(): FetchOutcome<List<TransactionEntity>> {
        val responses = try {
            transactionApi.getTransactions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FetchOutcome.Failure(e.toAppError().toPullResult())
        }

        val entities = responses.map { response ->
            response.toEntity() ?: return FetchOutcome.Failure(PullResult.NonRetryableFailure)
        }
        return FetchOutcome.Success(entities)
    }
}

/**
 * Per-resource fetch outcome, kept internal to [PullManager]. [Failure.result] is always
 * [PullResult.RetryableFailure] or [PullResult.NonRetryableFailure], never [PullResult.Success].
 */
private sealed interface FetchOutcome<out T> {
    data class Success<T>(val value: T) : FetchOutcome<T>
    data class Failure(val result: PullResult) : FetchOutcome<Nothing>
}
