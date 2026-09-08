package com.pahntd.expensetracker.repository

import com.pahntd.expensetracker.database.table.UserTable
import com.pahntd.expensetracker.model.User
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert

class ExposedUserRepository : UserRepository {
    override fun findByEmail(email: String): User? {
        return transaction {
            UserTable
                .selectAll()
                .where{ UserTable.email eq email }
                .singleOrNull()
                ?.let{row->
                    User(
                        id = row[UserTable.id],
                        email = row[UserTable.email],
                        passwordHash = row[UserTable.passwordHash],
                        createdAt = row[UserTable.createdAt],
                        updatedAt = row[UserTable.updatedAt]
                    )
                }
        }
    }

    override fun create(user: User): User {
        return transaction {
            UserTable.insert {
                it[id] = user.id
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[createdAt] = user.createdAt
                it[updatedAt] = user.updatedAt
            }
            user
        }
    }

    override fun findAll(): List<User> {
        return transaction {
            UserTable
                .selectAll()
                .map { row ->
                    User(
                        id = row[UserTable.id],
                        email = row[UserTable.email],
                        passwordHash = row[UserTable.passwordHash],
                        createdAt = row[UserTable.createdAt],
                        updatedAt = row[UserTable.updatedAt]
                    )
                }
        }
    }
}