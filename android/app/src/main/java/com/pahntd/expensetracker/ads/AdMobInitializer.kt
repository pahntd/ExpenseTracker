package com.pahntd.expensetracker.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.pahntd.expensetracker.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single entry point for starting ads: gathers UMP consent, then initializes the Next-Gen
 * Mobile Ads SDK once per process - only if [AdsConsentManager.canRequestAds] allows it.
 *
 * Fire-and-forget by design: nothing waits on it, the SDK is initialized on [applicationScope]
 * (off the main thread), and any failure just leaves [isInitialized] `false`. Future ad managers
 * should check [isInitialized] before loading an ad; the rest of the app never looks at it.
 */
@Singleton
class AdMobInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consentManager: AdsConsentManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    private val consentRequested = AtomicBoolean(false)
    private val initializationStarted = AtomicBoolean(false)

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    /**
     * Called from the host activity's `onCreate`. Consent is refreshed once per process (not on
     * every configuration change); SDK initialization starts immediately when consent from a
     * previous session already allows ads, otherwise after the consent flow completes.
     */
    fun start(activity: Activity) {
        if (!consentRequested.compareAndSet(false, true)) return

        consentManager.gatherConsent(activity) {
            initializeIfAllowed()
        }
        // Consent persisted from an earlier launch: start in parallel with the consent refresh.
        initializeIfAllowed()
    }

    private fun initializeIfAllowed() {
        if (!consentManager.canRequestAds()) return
        if (!initializationStarted.compareAndSet(false, true)) return

        applicationScope.launch {
            try {
                val config = InitializationConfig.Builder(AdsConfig.APP_ID)
                    .setRequestConfiguration(AdsConfig.requestConfiguration)
                    .build()
                MobileAds.initialize(context, config) {
                    _isInitialized.value = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mobile Ads SDK initialization failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "AdMobInitializer"
    }
}
