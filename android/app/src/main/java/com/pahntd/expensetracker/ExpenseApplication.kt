package com.pahntd.expensetracker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pahntd.expensetracker.data.network.NetworkMonitor
import com.pahntd.expensetracker.data.network.NetworkState
import com.pahntd.expensetracker.data.sync.SyncScheduler
import com.pahntd.expensetracker.data.sync.SyncState
import com.pahntd.expensetracker.data.sync.SyncStatusHolder
import com.pahntd.expensetracker.data.sync.SyncTrigger
import com.pahntd.expensetracker.di.ApplicationScope
import com.pahntd.expensetracker.utils.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltAndroidApp
class ExpenseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var syncStatusHolder: SyncStatusHolder

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applyTheme()
        observeReconnect()
        syncScheduler.schedulePeriodicSync()
    }

    /**
     * The one process-lifetime collector of [NetworkMonitor.networkState] in the app - keeping it
     * subscribed for the app's whole lifetime keeps [NetworkMonitor]'s underlying platform
     * callback registered continuously, so a reconnect is never missed between screens. This is
     * also the one place connectivity drives [SyncState] (see [updateSyncStateForConnectivity]) -
     * deliberately the same collector rather than a second subscription, so there is only ever
     * one place reacting to [NetworkMonitor.networkState] for the whole process.
     *
     * Fires [SyncTrigger.RECONNECTED] only on an actual `OFFLINE -> ONLINE` transition: [previous]
     * starts `null` (the initial state is never treated as a "reconnect"), and consecutive
     * `ONLINE` emissions can't even reach here since [NetworkMonitor] already de-duplicates
     * identical states upstream.
     */
    private fun observeReconnect() {
        var previous: NetworkState? = null
        networkMonitor.networkState
            .onEach { current ->
                val wasOffline = previous == NetworkState.OFFLINE
                previous = current
                if (wasOffline && current == NetworkState.ONLINE) {
                    syncScheduler.enqueueSync(SyncTrigger.RECONNECTED)
                }
                updateSyncStateForConnectivity(current)
            }
            .launchIn(applicationScope)
    }

    /**
     * Connectivity-only [SyncState] transitions - scheduling the actual sync pass on reconnect
     * remains [syncScheduler]'s job (see [observeReconnect]); this only ever reflects current
     * connectivity into [syncStatusHolder].
     *
     * Never overwrites [SyncState.SYNCING]: an active pass is
     * [com.pahntd.expensetracker.data.sync.SyncManager]'s to finalize, and it already reads this
     * same [NetworkMonitor] itself once the pass ends to decide between [SyncState.OFFLINE] and
     * [SyncState.SYNC_FAILED] - so a mid-pass connectivity blip here must not race ahead of that
     * decision.
     *
     * `ONLINE` only ever recovers a prior [SyncState.OFFLINE] back to [SyncState.IDLE]. It
     * deliberately never sets [SyncState.SYNCED] itself - reconnecting only makes a sync pass
     * *eligible* to run again (via [syncScheduler]), it does not mean one has actually completed -
     * and it never touches an existing [SyncState.SYNCED]/[SyncState.SYNC_FAILED] left over from
     * the last completed pass, since that describes that pass's outcome, not current connectivity.
     */
    private fun updateSyncStateForConnectivity(current: NetworkState) {
        when (current) {
            NetworkState.OFFLINE -> {
                if (syncStatusHolder.state.value != SyncState.SYNCING) {
                    syncStatusHolder.setState(SyncState.OFFLINE)
                }
            }
            NetworkState.ONLINE -> {
                if (syncStatusHolder.state.value == SyncState.OFFLINE) {
                    syncStatusHolder.setState(SyncState.IDLE)
                }
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun applyTheme() {
        val preferences = getSharedPreferences(
            AppPreferences.PREF_NAME,
            MODE_PRIVATE
        )
        val isDarkMode = preferences.getBoolean(
            AppPreferences.KEY_DARK_MODE, false
        )
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}