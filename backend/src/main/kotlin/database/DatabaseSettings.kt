package com.pahntd.expensetracker.database

import com.pahntd.expensetracker.config.AppEnvironment

/** Connection settings shared by Flyway and Exposed. */
data class DatabaseSettings(
    val url: String,
    val user: String,
    val password: String
) {
    // Keeps the password out of logs and exception messages.
    override fun toString(): String = "DatabaseSettings(url=$url, user=$user, password=***)"

    companion object {
        private const val DEV_URL = "jdbc:postgresql://localhost:5432/expense_tracker"
        private const val DEV_USER = "expense_tracker"
        private const val DEV_PASSWORD = "expense_tracker_dev_password" // matches docker-compose.yml

        /**
         * DEVELOPMENT: missing variables fall back to the local docker-compose database.
         * PRODUCTION: DATABASE_URL, DATABASE_USER and DATABASE_PASSWORD are all required.
         */
        fun fromEnvironment(
            environment: AppEnvironment = AppEnvironment.current(),
            getenv: (String) -> String? = System::getenv
        ): DatabaseSettings {
            val url = getenv("DATABASE_URL")?.takeIf { it.isNotBlank() }
            val user = getenv("DATABASE_USER")?.takeIf { it.isNotBlank() }
            val password = getenv("DATABASE_PASSWORD")?.takeIf { it.isNotBlank() }

            if (environment == AppEnvironment.PRODUCTION) {
                val missing = listOfNotNull(
                    "DATABASE_URL".takeIf { url == null },
                    "DATABASE_USER".takeIf { user == null },
                    "DATABASE_PASSWORD".takeIf { password == null }
                )
                if (missing.isNotEmpty()) {
                    throw IllegalStateException(
                        "Missing required environment variables for APP_ENV=production: " +
                            missing.joinToString()
                    )
                }
            }

            return DatabaseSettings(
                url = url ?: DEV_URL,
                user = user ?: DEV_USER,
                password = password ?: DEV_PASSWORD
            )
        }
    }
}
