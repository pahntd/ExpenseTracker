package com.pahntd.expensetracker

import com.pahntd.expensetracker.database.DatabaseFactory
import com.pahntd.expensetracker.database.FlywayConfig
import com.pahntd.expensetracker.plugins.configureAuthentication
import com.pahntd.expensetracker.plugins.configureSerialization
import com.pahntd.expensetracker.plugins.configureStatusPages
import com.pahntd.expensetracker.route.configureRouting
import io.ktor.server.application.Application

fun Application.rootModule() {
    FlywayConfig.migrate()
    DatabaseFactory.init()
    configureSerialization()
    configureStatusPages()
    configureAuthentication()
    configureRouting()
    // $env:JWT_SECRET="your-super-secret-key-for-local-development-only"
}
