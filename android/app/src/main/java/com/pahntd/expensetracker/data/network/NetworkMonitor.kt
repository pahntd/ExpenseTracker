package com.pahntd.expensetracker.data.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Reports the device's current [NetworkState] as a hot [StateFlow].
 *
 * This is purely observational: it knows nothing about sync, retries, navigation, or any other
 * business logic, and it never triggers network calls of its own. Consumers should treat
 * [networkState] as a signal to react to, not a thing to poll.
 */
interface NetworkMonitor {

    /**
     * The current [NetworkState]. Because this is a [StateFlow], [StateFlow.value] and the first
     * emission to any collector both reflect the real, current state immediately - callers never
     * have to wait for the next connectivity change to learn where things stand.
     */
    val networkState: StateFlow<NetworkState>
}
