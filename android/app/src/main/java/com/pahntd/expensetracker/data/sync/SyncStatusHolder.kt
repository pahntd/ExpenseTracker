package com.pahntd.expensetracker.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-local holder for the current [SyncState]. Not yet written to by anything - integrating
 * a writer (intended to be [SyncManager]) and any reader (intended to be Home) is a later step.
 *
 * `@Singleton` so every injection site shares the same instance/[state]; in-memory only, by
 * design - [SyncState] describes this process's current sync activity, not a durable fact about
 * the account, so it is never persisted and always starts at [SyncState.IDLE] on a fresh process.
 */
@Singleton
class SyncStatusHolder @Inject constructor() {

    private val _state = MutableStateFlow(SyncState.IDLE)

    /** The current or most recent sync pass's [SyncState]. */
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Sets the current [SyncState]. */
    fun setState(state: SyncState) {
        _state.value = state
    }

    /** Resets to [SyncState.IDLE]. */
    fun reset() {
        _state.value = SyncState.IDLE
    }
}
