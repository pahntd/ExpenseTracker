package com.pahntd.expensetracker.database.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object UserTable : Table("users") {
    val id = uuid("id")

    val email = varchar("email", 255)

    val passwordHash = varchar("password_hash", 255)

    val createdAt = timestampWithTimeZone("created_at")

    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}