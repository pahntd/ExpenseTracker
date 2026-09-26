package com.pahntd.expensetracker.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.pahntd.expensetracker.ads.AdsConfig.FEATURE_UNLOCK_DURATION_MILLIS
import com.pahntd.expensetracker.utils.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Time-limited, per-feature unlocks granted by rewarded ads.
 *
 * State is local only: one `Long` expiry timestamp per [LockedFeature] in the app's
 * SharedPreferences ([AppPreferences.PREF_NAME]). It is deliberately unrelated to Room, sync
 * and the user id, so it survives app restarts but never leaves the device. It does not survive
 * a logout (manual or forced): [com.pahntd.expensetracker.utils.AccountPreferencesCleaner]
 * revokes every unlock then, and [unlockChanges] tells an open screen about it.
 *
 * The only way to unlock is [requestUnlock], and it writes the expiry solely from
 * [RewardedAdManager]'s `onRewardEarned` callback - never on ad load, show, dismiss or failure.
 */
@Singleton
class FeatureUnlockManager @Inject constructor(
    @ApplicationContext context: Context,
    private val rewardedAdManager: RewardedAdManager
) {

    private val preferences = context.getSharedPreferences(
        AppPreferences.PREF_NAME,
        Context.MODE_PRIVATE
    )

    /** Whether [feature] has an unexpired unlock right now. Expired or missing = locked. */
    fun isUnlocked(feature: LockedFeature): Boolean = remainingUnlockMillis(feature) > 0

    /**
     * Whether an unlock request is still waiting on its ad (the ad is on screen). A new
     * [requestUnlock] made meanwhile would be ignored without any callback, so callers check this.
     */
    val isUnlockInProgress: Boolean
        get() = rewardedAdManager.isShowingAd

    /**
     * Emits whenever any feature's stored unlock is written or removed - including a revoke by
     * a forced logout while the user is still on screen. Expiry by time alone is not a write, so
     * callers still schedule their own re-check via [remainingUnlockMillis].
     */
    fun unlockChanges(): Flow<Unit> = callbackFlow {
        // SharedPreferences keeps listeners weakly; this local keeps it alive until awaitClose.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(AppPreferences.KEY_FEATURE_UNLOCK_EXPIRES_AT_PREFIX)) {
                trySend(Unit)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Milliseconds until [feature]'s unlock expires, or 0 if it is locked. */
    fun remainingUnlockMillis(feature: LockedFeature): Long {
        val expiresAt = preferences.getLong(keyFor(feature), 0L)
        val remaining = expiresAt - System.currentTimeMillis()
        // remaining > UNLOCK_DURATION only if the device clock was moved backwards after
        // unlocking; treat that as locked rather than letting the unlock outlive its window.
        return if (remaining in 1..FEATURE_UNLOCK_DURATION_MILLIS) remaining else 0L
    }

    /**
     * Shows a rewarded ad and, only if the reward is earned, unlocks [feature] (and nothing else)
     * for [FEATURE_UNLOCK_DURATION_MILLIS] from now. Earning again while unlocked restarts the window.
     *
     * Must be called on the main thread; all callbacks are delivered on the main thread.
     */
    fun requestUnlock(
        activity: Activity,
        feature: LockedFeature,
        onUnlocked: () -> Unit,
        onAdUnavailable: () -> Unit,
        onDismissedWithoutReward: () -> Unit = {}
    ) {
        rewardedAdManager.showRewarded(
            activity = activity,
            onRewardEarned = {
                grantUnlock(feature)
                onUnlocked()
            },
            onAdUnavailable = onAdUnavailable,
            onDismissedWithoutReward = onDismissedWithoutReward
        )
    }

    private fun grantUnlock(feature: LockedFeature) {
        preferences.edit {
            putLong(keyFor(feature), System.currentTimeMillis() + FEATURE_UNLOCK_DURATION_MILLIS)
        }
    }

    private fun keyFor(feature: LockedFeature): String =
        AppPreferences.KEY_FEATURE_UNLOCK_EXPIRES_AT_PREFIX + feature.id

}
