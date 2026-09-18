package com.pahntd.expensetracker

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pahntd.expensetracker.data.network.NetworkMonitor
import com.pahntd.expensetracker.data.network.NetworkState
import com.pahntd.expensetracker.data.sync.SyncScheduler
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
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applyTheme()
        observeReconnect()
    }

    /**
     * The one process-lifetime collector of [NetworkMonitor.networkState] in the app - keeping it
     * subscribed for the app's whole lifetime keeps [NetworkMonitor]'s underlying platform
     * callback registered continuously, so a reconnect is never missed between screens.
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
            }
            .launchIn(applicationScope)
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