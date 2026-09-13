package com.pahntd.expensetracker.service

import com.pahntd.expensetracker.model.Category
import com.pahntd.expensetracker.repository.CategoryRepository
import com.pahntd.expensetracker.repository.TransactionRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.uuid.Uuid

private const val MAX_NAME_LENGTH = 100
private const val MAX_ICON_LENGTH = 100

class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository
) {
    fun create(
        userId: Uuid,
        name: String,
        icon: String
    ): Category {
        val trimmedName = validateName(name)
        val trimmedIcon = validateIcon(icon)
        ensureNameNotTaken(userId, trimmedName, excludingId = null)

        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val category = Category(
            id = Uuid.random(),
            userId = userId,
            name = trimmedName,
            icon = trimmedIcon,
            createdAt = now,
            updatedAt = now
        )

        return categoryRepository.create(category)
    }

    fun getById(userId: Uuid, id: Uuid): Category {
        return categoryRepository.findById(id, userId)
            ?: throw NoSuchElementException("Category not found")
    }

    fun getAll(userId: Uuid): List<Category> {
        return categoryRepository.findAll(userId)
    }

    fun update(
        userId: Uuid,
        id: Uuid,
        name: String,
        icon: String
    ): Category {
        val trimmedName = validateName(name)
        val trimmedIcon = validateIcon(icon)
        ensureNameNotTaken(userId, trimmedName, excludingId = id)

        val updatedAt = OffsetDateTime.now(ZoneOffset.UTC)

        return categoryRepository.update(
            id = id,
            userId = userId,
            name = trimmedName,
            icon = trimmedIcon,
            updatedAt = updatedAt
        ) ?: throw NoSuchElementException("Category not found")
    }

    fun delete(userId: Uuid, id: Uuid) {
        categoryRepository.findById(id, userId)
            ?: throw NoSuchElementException("Category not found")

        if (transactionRepository.existsByCategoryId(id, userId)) {
            throw IllegalStateException(
                "Category is referenced by one or more transactions and cannot be deleted"
            )
        }

        val deleted = categoryRepository.delete(id, userId)
        if (!deleted) {
            throw NoSuchElementException("Category not found")
        }
    }

    private fun validateName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Category name must not be blank")
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            throw IllegalArgumentException("Category name must be at most $MAX_NAME_LENGTH characters")
        }
        return trimmed
    }

    private fun validateIcon(icon: String): String {
        val trimmed = icon.trim()
        if (trimmed.isBlank()) {
            throw IllegalArgumentException("Category icon must not be blank")
        }
        if (trimmed.length > MAX_ICON_LENGTH) {
            throw IllegalArgumentException("Category icon must be at most $MAX_ICON_LENGTH characters")
        }
        return trimmed
    }

    private fun ensureNameNotTaken(userId: Uuid, name: String, excludingId: Uuid?) {
        val existing = categoryRepository.findByUserIdAndName(userId, name)
        if (existing != null && existing.id != excludingId) {
            throw IllegalArgumentException("Category name '$name' already exists for this user")
        }
    }
}
