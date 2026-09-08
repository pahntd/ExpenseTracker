package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.model.User

interface UserRepository {
    fun findByEmail(email: String): User?
    fun create(user: User): User
    fun findAll(): List<User>
}