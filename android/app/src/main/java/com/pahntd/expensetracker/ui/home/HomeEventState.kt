package com.pahntd.expensetracker.ui.home

sealed interface HomeEventState {

    /** Manual sync was requested while offline - it's queued and runs once back online. */
    data object SyncQueuedOffline : HomeEventState
}
