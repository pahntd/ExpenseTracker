package com.pahntd.expensetracker.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/**
 * [NetworkMonitor] backed by [ConnectivityManager.NetworkCallback]. No polling or periodic pings:
 * the platform pushes every connectivity change to [callback].
 *
 * A network only counts as [NetworkState.ONLINE] once the platform reports it as both
 * [NetworkCapabilities.NET_CAPABILITY_INTERNET] and [NetworkCapabilities.NET_CAPABILITY_VALIDATED]
 * - i.e. actually reachable, not just an association with no path out (e.g. a Wi-Fi captive
 * portal or a router with no WAN link).
 *
 * [networkState] is a [StateFlow] seeded with [currentNetworkState] computed synchronously at
 * construction time, so [StateFlow.value] is correct immediately, before the callback has ever
 * fired. The [ConnectivityManager.NetworkCallback] itself is only registered while [networkState]
 * has active collectors - [SharingStarted.WhileSubscribed] ties `registerNetworkCallback` /
 * `unregisterNetworkCallback` to the upstream flow's `awaitClose`, so the callback can never
 * outlive its subscribers.
 */
class NetworkMonitorImpl(
    context: Context,
    applicationScope: CoroutineScope,
) : NetworkMonitor {

    private val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override val networkState: StateFlow<NetworkState> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(NetworkState.OFFLINE)
            awaitClose { }
            return@callbackFlow
        }

        // Networks currently held to be Internet-validated. A network is removed the instant it
        // is lost or stops validating, so "any left" is exactly ONLINE.
        val validatedNetworks = mutableSetOf<Network>()

        fun sendState() {
            trySend(if (validatedNetworks.isEmpty()) NetworkState.OFFLINE else NetworkState.ONLINE)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                validatedNetworks.remove(network)
                sendState()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val isUsable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (isUsable) validatedNetworks.add(network) else validatedNetworks.remove(network)
                sendState()
            }

            override fun onUnavailable() {
                sendState()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
        .distinctUntilChanged()
        .stateIn(
            scope = applicationScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = currentNetworkState(),
        )

    private fun currentNetworkState(): NetworkState {
        val manager = connectivityManager ?: return NetworkState.OFFLINE
        val network = manager.activeNetwork ?: return NetworkState.OFFLINE
        val capabilities = manager.getNetworkCapabilities(network) ?: return NetworkState.OFFLINE
        val isUsable = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return if (isUsable) NetworkState.ONLINE else NetworkState.OFFLINE
    }
}
