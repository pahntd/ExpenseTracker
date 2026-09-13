package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.database.table.CategoryTable
import com.pahntd.expensetracker.model.Category
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class ExposedCategoryRepository : CategoryRepository {

    override fun create(category: Category): Category {
        return transaction {
            CategoryTable.insert {
                it[id] = category.id
                it[userId] = category.userId
                it[name] = category.name
                it[icon] = category.icon
                it[createdAt] = category.createdAt
                it[updatedAt] = category.updatedAt
            }
            category
        }
    }

    override fun findById(id: Uuid, userId: Uuid): Category? {
        return transaction {
            CategoryTable
                .selectAll()
                .where { (CategoryTable.id eq id) and (CategoryTable.userId eq userId) }
                .singleOrNull()
                ?.let { row -> row.toCategory() }
        }
    }

    override fun findAll(userId: Uuid): List<Category> {
        return transaction {
            CategoryTable
                .selectAll()
                .where { CategoryTable.userId eq userId }
                .map { row -> row.toCategory() }
        }
    }

    override fun findByUserIdAndName(userId: Uuid, name: String): Category? {
        return transaction {
            CategoryTable
                .selectAll()
                .where { (CategoryTable.userId eq userId) and (CategoryTable.name eq name) }
                .singleOrNull()
                ?.let { row -> row.toCategory() }
        }
    }

    override fun update(
        id: Uuid,
        userId: Uuid,
        name: String,
        icon: String,
        updatedAt: OffsetDateTime
    ): Category? {
        return transaction {
            val updatedCount = CategoryTable.update(
                where = { (CategoryTable.id eq id) and (CategoryTable.userId eq userId) }
            ) {
                it[CategoryTable.name] = name
                it[CategoryTable.icon] = icon
                it[CategoryTable.updatedAt] = updatedAt
            }

            if (updatedCount == 0) {
                return@transaction null
            }

            CategoryTable
                .selectAll()
                .where { (CategoryTable.id eq id) and (CategoryTable.userId eq userId) }
                .singleOrNull()
                ?.let { row -> row.toCategory() }
        }
    }

    override fun delete(id: Uuid, userId: Uuid): Boolean {
        return transaction {
            CategoryTable.deleteWhere {
                (CategoryTable.id eq id) and (CategoryTable.userId eq userId)
            } > 0
        }
    }

    private fun ResultRow.toCategory(): Category {
        return Category(
            id = this[CategoryTable.id],
            userId = this[CategoryTable.userId],
            name = this[CategoryTable.name],
            icon = this[CategoryTable.icon],
            createdAt = this[CategoryTable.createdAt],
            updatedAt = this[CategoryTable.updatedAt]
        )
    }
}
