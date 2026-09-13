package com.pahntd.expensetracker.service

import com.pahntd.expensetracker.model.Transaction
import com.pahntd.expensetracker.model.TransactionType
import com.pahntd.expensetracker.repository.CategoryRepository
import com.pahntd.expensetracker.repository.TransactionRepository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

private const val MAX_TITLE_LENGTH = 255
private const val AMOUNT_SCALE = 2
private const val AMOUNT_MAX_INTEGER_DIGITS = 13

class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) {
    fun create(
        userId: Uuid,
        amount: BigDecimal,
        type: TransactionType,
        categoryId: Uuid?,
        date: OffsetDateTime,
        title: String?
    ): Transaction {
        validateAmount(amount)
        val trimmedTitle = validateTitle(title)

        if (categoryId != null) {
            ensureCategoryOwnedByUser(categoryId, userId)
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val transaction = Transaction(
            id = Uuid.random(),
            userId = userId,
            amount = amount,
            type = type,
            categoryId = categoryId,
            date = date,
            title = trimmedTitle,
            createdAt = now,
            updatedAt = now
        )

        return transactionRepository.create(transaction)
    }

    fun getById(userId: Uuid, id: Uuid): Transaction {
        return transactionRepository.findById(id, userId)
            ?: throw NoSuchElementException("Transaction not found")
    }

    fun getAll(userId: Uuid): List<Transaction> {
        return transactionRepository.findAll(userId)
    }

    fun update(
        userId: Uuid,
        id: Uuid,
        amount: BigDecimal,
        type: TransactionType,
        categoryId: Uuid?,
        date: OffsetDateTime,
        title: String?
    ): Transaction {
        validateAmount(amount)
        val trimmedTitle = validateTitle(title)

        if (categoryId != null) {
            ensureCategoryOwnedByUser(categoryId, userId)
        }

        val updatedAt = OffsetDateTime.now(ZoneOffset.UTC)

        return transactionRepository.update(
            id = id,
            userId = userId,
            amount = amount,
            type = type,
            categoryId = categoryId,
            date = date,
            title = trimmedTitle,
            updatedAt = updatedAt
        ) ?: throw NoSuchElementException("Transaction not found")
    }

    fun delete(userId: Uuid, id: Uuid) {
        val deleted = transactionRepository.delete(id, userId)
        if (!deleted) {
            throw NoSuchElementException("Transaction not found")
        }
    }

    private fun ensureCategoryOwnedByUser(categoryId: Uuid, userId: Uuid) {
        categoryRepository.findById(categoryId, userId)
            ?: throw IllegalArgumentException("Category does not exist or does not belong to this user")
    }

    private fun validateAmount(amount: BigDecimal) {
        if (amount.signum() <= 0) {
            throw IllegalArgumentException("Amount must be greater than zero")
        }
        if (amount.scale() > AMOUNT_SCALE) {
            throw IllegalArgumentException("Amount must have at most $AMOUNT_SCALE decimal places")
        }
        val integerDigits = amount.precision() - amount.scale()
        if (integerDigits > AMOUNT_MAX_INTEGER_DIGITS) {
            throw IllegalArgumentException("Amount exceeds the maximum allowed magnitude")
        }
    }

    private fun validateTitle(title: String?): String? {
        if (title == null) {
            return null
        }
        val trimmed = title.trim()
        if (trimmed.length > MAX_TITLE_LENGTH) {
            throw IllegalArgumentException("Title must be at most $MAX_TITLE_LENGTH characters")
        }
        return trimmed
    }
}
