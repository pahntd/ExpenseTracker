package com.pahntd.expensetracker.config

/**
 * Runtime mode, selected by the APP_ENV environment variable.
 * Unset/blank means DEVELOPMENT so the local workflow needs no extra setup;
 * deployments must set APP_ENV=production to get fail-fast configuration.
 */
enum class AppEnvironment {
    DEVELOPMENT,
    PRODUCTION;

    companion object {
        fun from(value: String?): AppEnvironment {
            return when (value?.trim()?.lowercase()) {
                null, "", "development" -> DEVELOPMENT
                "production" -> PRODUCTION
                else -> throw IllegalStateException(
                    "Invalid APP_ENV '$value'. Expected 'development' or 'production'."
                )
            }
        }

        fun current(): AppEnvironment = from(System.getenv("APP_ENV"))
    }
}
