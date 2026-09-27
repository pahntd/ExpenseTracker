package com.pahntd.expensetracker.config

object ServerConfig {
    const val HOST = "0.0.0.0"
    const val DEFAULT_PORT = 8080

    /** Unset/blank PORT falls back to [DEFAULT_PORT]; anything else must be a valid TCP port. */
    fun port(value: String?): Int {
        if (value.isNullOrBlank()) {
            return DEFAULT_PORT
        }

        val port = value.trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            throw IllegalStateException(
                "Invalid PORT '$value'. Expected an integer between 1 and 65535."
            )
        }
        return port
    }
}
