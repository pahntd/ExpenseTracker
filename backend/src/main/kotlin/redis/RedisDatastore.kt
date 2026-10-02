package com.pahntd.expensetracker.redis

import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SocketOptions
import io.lettuce.core.TimeoutOptions
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.coroutines.future.await
import java.io.Closeable
import java.time.Duration

/**
 * Application-wide Redis/Valkey connection.
 *
 * Lettuce multiplexes every command over one thread-safe connection, so a single instance is
 * created at startup and shared by all requests; it is closed when the application stops.
 * Lettuce reconnects automatically after a dropped connection.
 */
class RedisDatastore private constructor(
    private val client: RedisClient,
    private val connection: StatefulRedisConnection<String, String>
) : Closeable {

    private val commands = connection.async()

    suspend fun ping(): String = commands.ping().await()

    /** Runs a Lua script (executed atomically by Redis) that returns an array of integers. */
    suspend fun evalLongs(script: String, keys: List<String>, args: List<String>): List<Long> {
        return commands.eval<List<Long>>(
            script,
            ScriptOutputType.MULTI,
            keys.toTypedArray(),
            *args.toTypedArray()
        ).await()
    }

    suspend fun delete(key: String) {
        commands.del(key).await()
    }

    override fun close() {
        connection.close()
        client.shutdown(Duration.ZERO, SHUTDOWN_TIMEOUT)
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        private val COMMAND_TIMEOUT: Duration = Duration.ofSeconds(2)
        private val SHUTDOWN_TIMEOUT: Duration = Duration.ofSeconds(2)

        /**
         * Connects and verifies the server answers PING. Throws if Redis is unreachable, so the
         * application fails at startup instead of running without its rate limits.
         */
        fun connect(settings: RedisSettings): RedisDatastore {
            val client = RedisClient.create(RedisURI.create(settings.url))
            client.options = ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(CONNECT_TIMEOUT).build())
                // A request must never hang on Redis: commands fail after COMMAND_TIMEOUT...
                .timeoutOptions(TimeoutOptions.enabled(COMMAND_TIMEOUT))
                // ...and fail immediately while disconnected instead of queueing in memory.
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build()

            val connection = try {
                client.connect()
            } catch (e: Exception) {
                client.shutdown(Duration.ZERO, SHUTDOWN_TIMEOUT)
                throw connectionFailure(settings, e)
            }

            val datastore = RedisDatastore(client, connection)
            try {
                connection.sync().ping()
            } catch (e: Exception) {
                datastore.close()
                throw connectionFailure(settings, e)
            }
            return datastore
        }

        private fun connectionFailure(settings: RedisSettings, cause: Exception) =
            IllegalStateException(
                "Cannot connect to Redis at ${settings.redactedUrl()}: ${cause.message}", cause
            )
    }
}
