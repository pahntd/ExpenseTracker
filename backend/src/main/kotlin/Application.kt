package com.pahntd.expensetracker

import com.pahntd.expensetracker.config.AppEnvironment
import com.pahntd.expensetracker.database.DatabaseFactory
import com.pahntd.expensetracker.database.DatabaseSettings
import com.pahntd.expensetracker.database.FlywayConfig
import com.pahntd.expensetracker.plugins.configureAuthentication
import com.pahntd.expensetracker.plugins.configureSerialization
import com.pahntd.expensetracker.plugins.configureStatusPages
import com.pahntd.expensetracker.route.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.application.log

fun Application.rootModule() {
    val environment = AppEnvironment.current()
    log.info("Starting in $environment mode")

    val databaseSettings = DatabaseSettings.fromEnvironment(environment)
    FlywayConfig.migrate(databaseSettings)
    DatabaseFactory.init(databaseSettings)
    configureSerialization()
    configureStatusPages()
    configureAuthentication()
    configureRouting()
}
