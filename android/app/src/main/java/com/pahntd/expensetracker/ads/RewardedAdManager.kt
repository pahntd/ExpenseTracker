package com.pahntd.expensetracker.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.pahntd.expensetracker.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps at most one rewarded ad ready and shows it on request. Callers only see plain callbacks -
 * no SDK types leave this class.
 *
 * The one rule this class exists to enforce: `onRewardEarned` is invoked ONLY from the SDK's
 * `OnUserEarnedRewardListener`, at most once per show. Loading, caching, showing, clicking or
 * dismissing an ad never grants anything.
 *
 * Threading: [showRewarded] / [isAdAvailable] must be called on the main thread; SDK callbacks
 * (which may arrive on a background thread) are posted to the main thread before touching state,
 * so all mutable state below is main-thread-only. No Activity is stored - the one passed to
 * [showRewarded] is handed straight to the SDK.
 */
@Singleton
class RewardedAdManager @Inject constructor(
    private val adMobInitializer: AdMobInitializer,
    @ApplicationScope applicationScope: CoroutineScope
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var rewardedAd: RewardedAd? = null
    private var loadedAtElapsedMillis = 0L
    private var isLoading = false
    private var isShowing = false

    init {
        // Preload as soon as consent + SDK init allow it; never blocks anything.
        applicationScope.launch {
            adMobInitializer.isInitialized.first { it }
            mainHandler.post { loadIfNeeded() }
        }
    }

    /** Whether a rewarded ad is currently on screen (its show has not resolved yet). */
    val isShowingAd: Boolean
        get() = isShowing

    /** Whether a non-expired rewarded ad is ready to show right now. */
    fun isAdAvailable(): Boolean {
        val ad = rewardedAd ?: return false
        if (SystemClock.elapsedRealtime() - loadedAtElapsedMillis > AD_EXPIRY_MILLIS) {
            ad.destroy()
            rewardedAd = null
            return false
        }
        return true
    }

    /**
     * Shows the cached rewarded ad.
     *
     * - [onRewardEarned]: the user earned the reward (SDK reward callback) - the only place a
     *   feature may be unlocked.
     * - [onAdUnavailable]: no ad was ready, or it failed to show. Nothing is granted.
     * - [onDismissedWithoutReward]: the ad was closed before the reward was earned. Nothing is
     *   granted.
     *
     * A call made while an ad is already on screen is ignored; that show's own callbacks are
     * still delivered to its original caller.
     */
    fun showRewarded(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdUnavailable: () -> Unit,
        onDismissedWithoutReward: () -> Unit = {}
    ) {
        if (isShowing) return

        if (!isAdAvailable()) {
            loadIfNeeded()
            onAdUnavailable()
            return
        }
        val ad = rewardedAd ?: return onAdUnavailable()
        // A shown ad is spent: drop it now so it can never be shown (or counted) twice.
        rewardedAd = null
        isShowing = true

        var rewardEarned = false
        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                mainHandler.post {
                    finishShow(ad)
                    if (!rewardEarned) onDismissedWithoutReward()
                }
            }

            override fun onAdFailedToShowFullScreenContent(
                fullScreenContentError: FullScreenContentError
            ) {
                mainHandler.post {
                    Log.w(TAG, "Rewarded failed to show: ${fullScreenContentError.code} ${fullScreenContentError.message}")
                    finishShow(ad)
                    if (!rewardEarned) onAdUnavailable()
                }
            }
        }

        try {
            ad.show(activity) { _ ->
                mainHandler.post {
                    if (!rewardEarned) {
                        rewardEarned = true
                        onRewardEarned()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Rewarded show threw", e)
            finishShow(ad)
            onAdUnavailable()
        }
    }

    private fun finishShow(ad: RewardedAd) {
        if (!isShowing) return
        isShowing = false
        ad.adEventCallback = null
        ad.destroy()
        loadIfNeeded()
    }

    /** Loads the next ad unless one is cached, loading, or the SDK isn't initialized yet. */
    private fun loadIfNeeded() {
        if (isLoading || isAdAvailable() || !adMobInitializer.isInitialized.value) return
        isLoading = true

        try {
            RewardedAd.load(
                AdRequest.Builder(AdsConfig.REWARDED_AD_UNIT_ID).build(),
                object : AdLoadCallback<RewardedAd> {
                    override fun onAdLoaded(ad: RewardedAd) {
                        mainHandler.post {
                            isLoading = false
                            rewardedAd = ad
                            loadedAtElapsedMillis = SystemClock.elapsedRealtime()
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        mainHandler.post {
                            // No retry loop: the next showRewarded() request triggers a new load.
                            isLoading = false
                            Log.w(TAG, "Rewarded failed to load: ${adError.code} ${adError.message}")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            isLoading = false
            Log.w(TAG, "Rewarded load threw", e)
        }
    }

    private companion object {
        const val TAG = "RewardedAdManager"

        /** Google's guidance: a cached rewarded ad should not be shown after about an hour. */
        val AD_EXPIRY_MILLIS: Long = TimeUnit.HOURS.toMillis(1)
    }
}
