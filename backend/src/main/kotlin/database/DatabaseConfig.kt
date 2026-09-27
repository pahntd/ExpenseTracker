package com.pahntd.expensetracker.database

import org.jetbrains.exposed.v1.jdbc.Database

object DatabaseConfig {
    fun connect(settings: DatabaseSettings): Database {
        return Database.connect(
            url = settings.url,
            driver = "org.postgresql.Driver",
            user = settings.user,
            password = settings.password
        )
    }
}
