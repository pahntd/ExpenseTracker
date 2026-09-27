package com.pahntd.expensetracker.database

import org.flywaydb.core.Flyway

object FlywayConfig {
    fun migrate(settings: DatabaseSettings) {
        Flyway.configure()
            .dataSource(settings.url, settings.user, settings.password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}
