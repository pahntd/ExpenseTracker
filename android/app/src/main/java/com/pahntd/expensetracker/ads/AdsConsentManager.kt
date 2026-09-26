package com.pahntd.expensetracker.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.pahntd.expensetracker.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around Google's User Messaging Platform. Consent is an ads-only concern: nothing
 * outside the `ads` package reads it, and every failure path here ends in "no ads", never in an
 * exception reaching the caller.
 *
 * UMP's update/form APIs are asynchronous and must be called on the main thread - they never
 * block it.
 */
@Singleton
class AdsConsentManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /**
     * Whether ads may be requested. UMP persists the last consent result, so this is already
     * meaningful at launch (before [gatherConsent] finishes) for a user who consented earlier.
     */
    fun canRequestAds(): Boolean = runCatching { consentInformation.canRequestAds() }
        .getOrDefault(false)

    /** Whether Settings must offer a "Privacy options" entry (e.g. for EEA users). */
    fun isPrivacyOptionsRequired(): Boolean = runCatching {
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }.getOrDefault(false)

    /**
     * Refreshes the consent state and shows the consent form if one is required, then calls
     * [onComplete] exactly once - whether consent was gathered, was not needed, or failed.
     * Callers decide what to do via [canRequestAds]; a failure only means ads stay off.
     */
    fun gatherConsent(activity: Activity, onComplete: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .setAdMobAppId(AdsConfig.APP_ID)
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (BuildConfig.DEBUG) setConsentDebugSettings(debugSettings(activity))
            }
            .build()

        try {
            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                        formError?.let { Log.w(TAG, "Consent form: ${it.errorCode} ${it.message}") }
                        onComplete()
                    }
                },
                { requestError ->
                    Log.w(TAG, "Consent info update: ${requestError.errorCode} ${requestError.message}")
                    onComplete()
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Consent info update failed", e)
            onComplete()
        }
    }

    /** Re-opens the consent form from a user action (the Settings "Privacy options" entry). */
    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit = {}) {
        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                formError?.let { Log.w(TAG, "Privacy options form: ${it.errorCode} ${it.message}") }
                onDismissed()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Privacy options form failed", e)
            onDismissed()
        }
    }

    private fun debugSettings(context: Context): ConsentDebugSettings =
        ConsentDebugSettings.Builder(context)
            .setDebugGeography(AdsConfig.CONSENT_DEBUG_GEOGRAPHY)
            .apply { AdsConfig.CONSENT_TEST_DEVICE_HASHED_IDS.forEach(::addTestDeviceHashedId) }
            .build()

    private companion object {
        const val TAG = "AdsConsentManager"
    }
}
