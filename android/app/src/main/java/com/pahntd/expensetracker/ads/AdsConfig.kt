package com.pahntd.expensetracker.ads

import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.pahntd.expensetracker.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * The one place ad configuration is read from. The IDs themselves live in `app/build.gradle.kts`
 * (Google's public test IDs during development); UI and business classes must go through this
 * object instead of hard-coding ad unit IDs.
 *
 * Ads are a presentation concern only: nothing in Room, sync, auth or statistic data loading may
 * depend on this package, so the core app keeps working when ads fail to load.
 */
object AdsConfig {

    /** Passed to both the Next-Gen SDK ([AdMobInitializer]) and UMP ([AdsConsentManager]). */
    const val APP_ID: String = BuildConfig.ADMOB_APP_ID

    const val BANNER_AD_UNIT_ID: String = BuildConfig.ADMOB_BANNER_UNIT_ID
    const val REWARDED_AD_UNIT_ID: String = BuildConfig.ADMOB_REWARDED_UNIT_ID

    /** How long a successful rewarded-ad reward unlocks the requested feature. */
    val FEATURE_UNLOCK_DURATION_MILLIS: Long = TimeUnit.HOURS.toMillis(3)

    /** Keep requests suitable for a general audience; applied once when the SDK is initialized. */
    val requestConfiguration: RequestConfiguration
        get() = RequestConfiguration.Builder()
            .setMaxAdContentRating(RequestConfiguration.MaxAdContentRating.MAX_AD_CONTENT_RATING_G)
            .build()

    /**
     * UMP debug geography, applied to debug builds only. Switch to `DEBUG_GEOGRAPHY_EEA` (plus a
     * hashed ID in [CONSENT_TEST_DEVICE_HASHED_IDS] for a physical device) to force the consent
     * form while developing.
     */
    const val CONSENT_DEBUG_GEOGRAPHY: Int = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED

    /** Hashed device IDs UMP logs to Logcat; only used by debug builds. */
    val CONSENT_TEST_DEVICE_HASHED_IDS: List<String> = emptyList()
}
