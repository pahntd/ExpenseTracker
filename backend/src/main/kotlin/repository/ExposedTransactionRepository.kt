package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.database.table.TransactionTable
import com.pahntd.expensetracker.model.Transaction
import com.pahntd.expensetracker.model.TransactionType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.uuid.Uuid

class ExposedTransactionRepository : TransactionRepository {

    override fun create(newTransaction: Transaction): Transaction {
        return transaction {
            TransactionTable.insert {
                it[id] = newTransaction.id
                it[userId] = newTransaction.userId
                it[amount] = newTransaction.amount
                it[type] = newTransaction.type
                it[categoryId] = newTransaction.categoryId
                it[date] = newTransaction.date
                it[title] = newTransaction.title
                it[createdAt] = newTransaction.createdAt
                it[updatedAt] = newTransaction.updatedAt
            }
            newTransaction
        }
    }

    override fun findById(id: Uuid, userId: Uuid): Transaction? {
        return transaction {
            TransactionTable
                .selectAll()
                .where { (TransactionTable.id eq id) and (TransactionTable.userId eq userId) }
                .singleOrNull()
                ?.let { row -> row.toTransaction() }
        }
    }

    override fun findAll(userId: Uuid): List<Transaction> {
        return transaction {
            TransactionTable
                .selectAll()
                .where { TransactionTable.userId eq userId }
                .map { row -> row.toTransaction() }
        }
    }

    override fun existsByCategoryId(categoryId: Uuid, userId: Uuid): Boolean {
        return transaction {
            TransactionTable
                .selectAll()
                .where { (TransactionTable.categoryId eq categoryId) and (TransactionTable.userId eq userId) }
                .limit(1)
                .count() > 0
        }
    }

    override fun update(
        id: Uuid,
        userId: Uuid,
        amount: BigDecimal,
        type: TransactionType,
        categoryId: Uuid?,
        date: OffsetDateTime,
        title: String?,
        updatedAt: OffsetDateTime
    ): Transaction? {
        return transaction {
            val updatedCount = TransactionTable.update(
                where = { (TransactionTable.id eq id) and (TransactionTable.userId eq userId) }
            ) {
                it[TransactionTable.amount] = amount
                it[TransactionTable.type] = type
                it[TransactionTable.categoryId] = categoryId
                it[TransactionTable.date] = date
                it[TransactionTable.title] = title
                it[TransactionTable.updatedAt] = updatedAt
            }

            if (updatedCount == 0) {
                return@transaction null
            }

            TransactionTable
                .selectAll()
                .where { (TransactionTable.id eq id) and (TransactionTable.userId eq userId) }
                .singleOrNull()
                ?.let { row -> row.toTransaction() }
        }
    }

    override fun delete(id: Uuid, userId: Uuid): Boolean {
        return transaction {
            TransactionTable.deleteWhere {
                (TransactionTable.id eq id) and (TransactionTable.userId eq userId)
            } > 0
        }
    }

    private fun ResultRow.toTransaction(): Transaction {
        return Transaction(
            id = this[TransactionTable.id],
            userId = this[TransactionTable.userId],
            amount = this[TransactionTable.amount],
            type = this[TransactionTable.type],
            categoryId = this[TransactionTable.categoryId],
            date = this[TransactionTable.date],
            title = this[TransactionTable.title],
            createdAt = this[TransactionTable.createdAt],
            updatedAt = this[TransactionTable.updatedAt]
        )
    }
}
