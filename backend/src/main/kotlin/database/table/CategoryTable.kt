package com.pahntd.expensetracker.database.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object CategoryTable : Table("categories") {

    val id = uuid("id")

    val userId = uuid("user_id")
        .references(UserTable.id)

    val name = varchar("name", 100)

    val icon = varchar("icon", 100)

    val createdAt = timestampWithTimeZone("created_at")

    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
