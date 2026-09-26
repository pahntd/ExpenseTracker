package com.pahntd.expensetracker.utils

import android.content.Context
import androidx.core.content.edit
import com.pahntd.expensetracker.ads.LockedFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single place that knows which [AppPreferences] keys belong to the signed-in account, and
 * removes them when that account's session ends. Any new account-scoped key must be added to
 * [clear]; app-scoped keys ([AppPreferences.KEY_DARK_MODE]) are deliberately left alone.
 *
 * After [clear]: no Home greeting name, the Statistics time filter falls back to
 * [com.pahntd.expensetracker.ui.statistics.StatisticTimeFilter.fromName]'s default, and every
 * [LockedFeature] is locked again (unlocks are device-local, not per user, so they never carry
 * over to the next login).
 *
 * Thread-safe (SharedPreferences), so it can be called from OkHttp's authenticator thread too.
 */
@Singleton
class AccountPreferencesCleaner @Inject constructor(
    @ApplicationContext context: Context
) {

    private val preferences = context.getSharedPreferences(
        AppPreferences.PREF_NAME,
        Context.MODE_PRIVATE
    )

    fun clear() {
        preferences.edit {
            remove(AppPreferences.KEY_USERNAME)
            remove(AppPreferences.KEY_STATISTIC_TIME_FILTER)
            LockedFeature.entries.forEach { feature ->
                remove(AppPreferences.KEY_FEATURE_UNLOCK_EXPIRES_AT_PREFIX + feature.id)
            }
        }
    }
}
