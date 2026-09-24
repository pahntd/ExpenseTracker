package com.pahntd.expensetracker.data.sync

/**
 * The status of the current or most recent sync pass, held process-locally by [SyncStatusHolder].
 *
 * This is a UI-facing signal only - it is never persisted and is not the source of truth for
 * which local data is pending. That remains entity-level
 * [com.pahntd.expensetracker.data.local.converter.SyncStatus] in Room; a row can be
 * `PENDING_*` there regardless of what [SyncState] currently reports.
 */
enum class SyncState {

    /** No sync pass is currently running and there is no active terminal result being reported. */
    IDLE,

    /** A sync pass is currently running. */
    SYNCING,

    /**
     * The latest completed sync pass finished without any Push/Pull failure.
     *
     * This does NOT mean the Room dataset is globally guaranteed to contain no pending changes -
     * a row created or edited after this pass completed can still be `PENDING_*` right now.
     */
    SYNCED,

    /**
     * The latest relevant sync failure happened while
     * [com.pahntd.expensetracker.data.network.NetworkMonitor] reported offline, or the app is
     * currently offline according to the agreed state transition policy.
     */
    OFFLINE,

    /** The latest sync pass encountered one or more failures while the device was not offline. */
    SYNC_FAILED
}
