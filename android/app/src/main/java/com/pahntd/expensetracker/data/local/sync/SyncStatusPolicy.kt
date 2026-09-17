package com.pahntd.expensetracker.data.local.sync

import com.pahntd.expensetracker.data.local.converter.SyncStatus

/**
 * Governs how a row's [SyncStatus] transitions in response to local mutations, so every Room
 * entity applies the same offline-first rules instead of each repository re-deriving them:
 *
 * SYNCED / PENDING_UPDATE  --update--> PENDING_UPDATE
 * PENDING_CREATE           --update--> PENDING_CREATE (server has never seen this row)
 * PENDING_DELETE                      not reachable here; callers must block mutation first
 */
object SyncStatusPolicy {

    /** The status a row should carry after a local update, given its current [status]. */
    fun onLocalUpdate(status: SyncStatus): SyncStatus =
        if (status == SyncStatus.PENDING_CREATE) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE

    /** A pending-delete row must not be edited or deleted again through the normal local flow. */
    fun canMutate(status: SyncStatus): Boolean = status != SyncStatus.PENDING_DELETE

    /** A row the server has never received is removed outright instead of being tombstoned. */
    fun shouldHardDeleteLocally(status: SyncStatus): Boolean = status == SyncStatus.PENDING_CREATE
}
