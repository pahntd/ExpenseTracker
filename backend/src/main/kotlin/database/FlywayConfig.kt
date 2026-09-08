package com.pahntd.expensetracker.database

import org.flywaydb.core.Flyway

object FlywayConfig {
    fun migrate() {
        val url = System.getenv("DATABASE_URL")
            ?: "jdbc:postgresql://localhost:5432/expense_tracker"

        val user = System.getenv("DATABASE_USER")
            ?: "expense_tracker"

        val password = System.getenv("DATABASE_PASSWORD")
            ?: "expense_tracker_dev_password"

        Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}