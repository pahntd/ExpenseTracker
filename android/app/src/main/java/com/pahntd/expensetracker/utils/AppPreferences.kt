package com.pahntd.expensetracker.utils

object AppPreferences {
    const val PREF_NAME = "app_preferences"
    const val KEY_DARK_MODE = "dark_mode"
    const val KEY_STATISTIC_TIME_FILTER = "statistic_time_filter"

    /** Display name for Home's greeting: the part of the login email before '@'. */
    const val KEY_USERNAME = "username"

    /**
     * Prefix for a rewarded feature unlock's expiry (epoch millis, Long). The full key is this
     * prefix + [com.pahntd.expensetracker.ads.LockedFeature.id], one key per feature.
     */
    const val KEY_FEATURE_UNLOCK_EXPIRES_AT_PREFIX = "feature_unlock_expires_at_"
}
