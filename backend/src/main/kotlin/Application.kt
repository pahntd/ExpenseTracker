package com.pahntd.expensetracker

import com.pahntd.expensetracker.config.AppEnvironment
import com.pahntd.expensetracker.database.DatabaseFactory
import com.pahntd.expensetracker.database.DatabaseSettings
import com.pahntd.expensetracker.database.FlywayConfig
import com.pahntd.expensetracker.plugins.configureAuthentication
import com.pahntd.expensetracker.plugins.configureRateLimiting
import com.pahntd.expensetracker.plugins.configureSerialization
import com.pahntd.expensetracker.plugins.configureStatusPages
import com.pahntd.expensetracker.ratelimit.ClientIpResolver
import com.pahntd.expensetracker.ratelimit.RateLimitService
import com.pahntd.expensetracker.ratelimit.RedisRateLimitStore
import com.pahntd.expensetracker.redis.RedisDatastore
import com.pahntd.expensetracker.redis.RedisSettings
import com.pahntd.expensetracker.route.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.log

/**
 * [clientIpResolver] defaults to the CLIENT_IP_HEADER configuration; tests pass their own so they
 * can simulate several clients without changing the environment.
 */
fun Application.rootModule(clientIpResolver: ClientIpResolver = ClientIpResolver.fromEnvironment()) {
    val environment = AppEnvironment.current()
    log.info("Starting in $environment mode")

    val databaseSettings = DatabaseSettings.fromEnvironment(environment)
    FlywayConfig.migrate(databaseSettings)
    DatabaseFactory.init(databaseSettings)

    // Like the database, Redis must be reachable at startup: the auth rate limits depend on it.
    val redisSettings = RedisSettings.fromEnvironment(environment)
    val redis = RedisDatastore.connect(redisSettings)
    monitor.subscribe(ApplicationStopped) { redis.close() }
    log.info("Connected to Redis at ${redisSettings.redactedUrl()}")

    configureSerialization()
    configureStatusPages()
    configureAuthentication()
    configureRateLimiting(
        service = RateLimitService(RedisRateLimitStore(redis)),
        clientIpResolver = clientIpResolver
    )
    configureRouting()
    // $env:JWT_SECRET="your-super-secret-key-for-local-development-only"
}
