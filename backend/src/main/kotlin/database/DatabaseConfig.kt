package com.pahntd.expensetracker.database

import org.jetbrains.exposed.v1.jdbc.Database

object DatabaseConfig {
    fun connect(): Database {
        val url = System.getenv("DATABASE_URL")
            ?: "jdbc:postgresql://localhost:5432/expense_tracker"

        val user = System.getenv("DATABASE_USER")
            ?: "expense_tracker"

        val password = System.getenv("DATABASE_PASSWORD")
            ?: "expense_tracker_dev_password"

        return Database.connect(
            url = url,
            driver = "org.postgresql.Driver",
            user = user,
            password = password
        )
    }
}