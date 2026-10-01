package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.model.User
import kotlin.uuid.Uuid

interface UserRepository {
    fun findByEmail(email: String): User?
    fun create(user: User): User

    /**
     * Locks the user row (SELECT ... FOR UPDATE) until the enclosing transaction ends.
     * Only meaningful inside an outer transaction. Returns false if the user doesn't exist.
     */
    fun lockById(id: Uuid): Boolean

    fun delete(id: Uuid): Boolean
}