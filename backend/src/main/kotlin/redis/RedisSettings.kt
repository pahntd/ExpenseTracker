package com.pahntd.expensetracker.redis

import com.pahntd.expensetracker.config.AppEnvironment
import java.net.URI

/** Connection settings for the Redis-compatible store (Render Key Value / Valkey in production). */
data class RedisSettings(
    val url: String
) {
    // Keeps a password embedded in the URL (redis://user:password@host) out of logs.
    override fun toString(): String = "RedisSettings(url=${redactedUrl()})"

    fun redactedUrl(): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return "***"
        val userInfo = uri.userInfo ?: return url
        return url.replace(userInfo, "***")
    }

    companion object {
        private const val DEV_URL = "redis://localhost:6379" // matches docker-compose.yml

        /**
         * DEVELOPMENT: a missing REDIS_URL falls back to the local docker-compose Redis.
         * PRODUCTION: REDIS_URL is required. There is no fallback, because a wrong store would
         * silently turn the shared rate limit into no protection at all.
         */
        fun fromEnvironment(
            environment: AppEnvironment = AppEnvironment.current(),
            getenv: (String) -> String? = System::getenv
        ): RedisSettings {
            val url = getenv("REDIS_URL")?.trim()?.takeIf { it.isNotBlank() }

            if (url == null && environment == AppEnvironment.PRODUCTION) {
                throw IllegalStateException(
                    "Missing required environment variables for APP_ENV=production: REDIS_URL"
                )
            }

            val resolved = url ?: DEV_URL
            if (!resolved.startsWith("redis://") && !resolved.startsWith("rediss://")) {
                throw IllegalStateException(
                    "Invalid REDIS_URL. Expected a redis:// or rediss:// URL."
                )
            }
            return RedisSettings(url = resolved)
        }
    }
}
