package com.pahntd.expensetracker.database

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseFactory {
    private lateinit var database: Database

    fun init(settings: DatabaseSettings) {
        database = DatabaseConfig.connect(settings)

        transaction(database) {
            exec("SELECT 1")
        }
    }
}
