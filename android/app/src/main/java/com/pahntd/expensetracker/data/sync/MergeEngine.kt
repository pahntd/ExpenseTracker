package com.pahntd.expensetracker.data.sync

import com.pahntd.expensetracker.data.local.converter.SyncStatus
import com.pahntd.expensetracker.data.local.dao.CategoryDao
import com.pahntd.expensetracker.data.local.dao.TransactionDao
import com.pahntd.expensetracker.data.local.entity.CategoryEntity
import com.pahntd.expensetracker.data.local.entity.TransactionEntity

/**
 * The merge decision for one local row that also has a server counterpart. A row with no local
 * counterpart (server-only) has no state to weigh and is always inserted as `SYNCED` directly by
 * the caller - it never goes through this decision.
 */
internal enum class MergeAction {
    /** Adopt the server row, replacing the local one (it already carries `syncStatus = SYNCED`). */
    USE_SERVER,

    /** Leave the local row exactly as it is; the server side is not applied. */
    KEEP_LOCAL,

    /** Remove the local row entirely - it no longer belongs in Room. */
    DELETE_LOCAL
}

/**
 * Decision table for one local row, given its [localSyncStatus]/[localUpdatedAt] and the same-id
 * server row's `updatedAt` ([serverUpdatedAt], `null` when the id is absent from the server
 * snapshot):
 *
 * | Local state     | Server exists | Timestamp relation | Result       |
 * |-----------------|---------------|---------------------|--------------|
 * | SYNCED          | yes           | any                 | USE_SERVER   |
 * | SYNCED          | no            | -                   | DELETE_LOCAL |
 * | PENDING_CREATE  | no            | -                   | KEEP_LOCAL   |
 * | PENDING_CREATE  | yes           | local > server      | KEEP_LOCAL   |
 * | PENDING_CREATE  | yes           | server >= local     | USE_SERVER   |
 * | PENDING_UPDATE  | yes           | local > server      | KEEP_LOCAL   |
 * | PENDING_UPDATE  | yes           | server >= local     | USE_SERVER   |
 * | PENDING_UPDATE  | no            | -                   | DELETE_LOCAL |
 * | PENDING_DELETE  | yes           | any                 | KEEP_LOCAL   |
 * | PENDING_DELETE  | no            | -                   | DELETE_LOCAL |
 *
 * Two things are easy to get wrong and are called out explicitly:
 *
 * 1. The tie `server.updatedAt == local.updatedAt` resolves to the server - `>=`, never `>`.
 *
 * 2. `PENDING_UPDATE` with the server missing resolves to `DELETE_LOCAL`, the same as `SYNCED`
 *    and `PENDING_DELETE` with the server missing. A row only reaches `PENDING_UPDATE` by editing
 *    a row that was already `SYNCED` (see [com.pahntd.expensetracker.data.local.sync.SyncStatusPolicy.onLocalUpdate]
 *    - `PENDING_CREATE` stays `PENDING_CREATE` on edit), so it necessarily existed on the server
 *    before this pull. If the server's complete snapshot no longer has it, it was deleted
 *    server-side (by this device or another) while the edit was still pending: pushing that update
 *    would 404 forever, so the delete wins and the local row is removed - the same reconciliation
 *    as an unconfirmed local delete agreeing with a server that's already gone (the `PENDING_DELETE`
 *    row). `PENDING_CREATE` is the only state kept on a server miss, because it's the only one
 *    whose absence proves nothing: it was never pushed successfully in the first place, so Push
 *    must still get another chance at it.
 */
internal fun decideMergeAction(
    localSyncStatus: SyncStatus,
    localUpdatedAt: Long,
    serverUpdatedAt: Long?
): MergeAction = when (localSyncStatus) {
    SyncStatus.SYNCED -> if (serverUpdatedAt != null) MergeAction.USE_SERVER else MergeAction.DELETE_LOCAL

    SyncStatus.PENDING_CREATE -> when {
        serverUpdatedAt == null -> MergeAction.KEEP_LOCAL
        serverUpdatedAt >= localUpdatedAt -> MergeAction.USE_SERVER
        else -> MergeAction.KEEP_LOCAL
    }

    SyncStatus.PENDING_UPDATE -> when {
        serverUpdatedAt == null -> MergeAction.DELETE_LOCAL
        serverUpdatedAt >= localUpdatedAt -> MergeAction.USE_SERVER
        else -> MergeAction.KEEP_LOCAL
    }

    SyncStatus.PENDING_DELETE -> if (serverUpdatedAt != null) MergeAction.KEEP_LOCAL else MergeAction.DELETE_LOCAL
}

/**
 * Merges a pulled server category snapshot into Room per [decideMergeAction]. Deletions are not
 * applied here: they're returned as candidate ids so the caller ([mergeSnapshot]) can defer them
 * until after transactions have been merged, since a surviving transaction may still reference one
 * ([TransactionEntity]'s `categoryId` foreign key is `RESTRICT`).
 */
internal suspend fun mergeCategories(
    categoryDao: CategoryDao,
    serverCategories: List<CategoryEntity>
): Set<String> {
    val localById = categoryDao.findAll().associateBy { it.id }
    val serverById = serverCategories.associateBy { it.id }
    val deletionCandidates = mutableSetOf<String>()

    for (id in localById.keys + serverById.keys) {
        val local = localById[id]
        val server = serverById[id]

        if (local == null) {
            categoryDao.insert(requireNotNull(server) { "id $id missing from both local and server maps" })
            continue
        }

        when (decideMergeAction(local.syncStatus, local.updatedAt, server?.updatedAt)) {
            MergeAction.USE_SERVER -> categoryDao.update(requireNotNull(server))
            MergeAction.KEEP_LOCAL -> Unit
            MergeAction.DELETE_LOCAL -> deletionCandidates += id
        }
    }
    return deletionCandidates
}

/** Merges a pulled server transaction snapshot into Room per [decideMergeAction]. */
internal suspend fun mergeTransactions(
    transactionDao: TransactionDao,
    serverTransactions: List<TransactionEntity>
) {
    val localById = transactionDao.findAll().associateBy { it.id }
    val serverById = serverTransactions.associateBy { it.id }

    for (id in localById.keys + serverById.keys) {
        val local = localById[id]
        val server = serverById[id]

        if (local == null) {
            transactionDao.insert(requireNotNull(server) { "id $id missing from both local and server maps" })
            continue
        }

        when (decideMergeAction(local.syncStatus, local.updatedAt, server?.updatedAt)) {
            MergeAction.USE_SERVER -> transactionDao.update(requireNotNull(server))
            MergeAction.KEEP_LOCAL -> Unit
            MergeAction.DELETE_LOCAL -> transactionDao.deleteById(id)
        }
    }
}

/**
 * Merges one full pull snapshot (both resources) into Room. Transactions are merged after
 * categories so that a server-only transaction's `categoryId` always has a parent row already in
 * place; a category [decideMergeAction] resolved as [MergeAction.DELETE_LOCAL] is only physically
 * removed once transactions have settled, and only if no transaction still references it -
 * mirroring [SyncManager]'s existing transactions-before-categories delete ordering on push. A
 * category still referenced by a surviving transaction is left in place for a later pull pass
 * rather than forced, so this never throws on the `categoryId` foreign key.
 *
 * Callers are expected to run this inside a single Room transaction (see [PullManager.pull]) so a
 * crash or cancellation mid-merge can't leave the two tables reconciled against different points
 * in time.
 */
internal suspend fun mergeSnapshot(
    categoryDao: CategoryDao,
    transactionDao: TransactionDao,
    serverCategories: List<CategoryEntity>,
    serverTransactions: List<TransactionEntity>
) {
    val categoryIdsPendingDeletion = mergeCategories(categoryDao, serverCategories)
    mergeTransactions(transactionDao, serverTransactions)
    categoryIdsPendingDeletion
        .filterNot { transactionDao.existsByCategory(it) }
        .forEach { categoryDao.deleteById(it) }
}
