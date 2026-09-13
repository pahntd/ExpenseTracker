package com.pahntd.expensetracker.database.table

import com.pahntd.expensetracker.model.TransactionType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object TransactionTable : Table("transactions") {

    val id = uuid("id")

    val userId = uuid("user_id")
        .references(UserTable.id)

    val amount = decimal("amount", 15, 2)

    val type = enumerationByName("type", 10, TransactionType::class)

    val categoryId = uuid("category_id")
        .references(CategoryTable.id)
        .nullable()

    val date = timestampWithTimeZone("date")

    val title = varchar("title", 255).nullable()

    val createdAt = timestampWithTimeZone("created_at")

    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}
