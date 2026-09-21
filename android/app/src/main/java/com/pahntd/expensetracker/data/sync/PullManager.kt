package com.pahntd.expensetracker.data.sync

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
 * Fetches one complete server snapshot - every category and every transaction - and maps it to
 * Room entities, without writing anything to Room or merging it against local state (Merge/Room
 * integration are handled elsewhere). Categories and transactions are fetched concurrently since
 * neither depends on the other's response; either request failing, or any single item failing to
 * map, invalidates the whole snapshot - there is no partial [PullResult.Success].
 */
class PullManager @Inject constructor(
    private val categoryApi: CategoryApi,
    private val transactionApi: TransactionApi
) {

    suspend fun pull(): PullResult = coroutineScope {
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
