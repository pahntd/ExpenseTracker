package com.pahntd.expensetracker.service

import com.pahntd.expensetracker.repository.CategoryRepository
import com.pahntd.expensetracker.repository.RefreshTokenRepository
import com.pahntd.expensetracker.repository.TransactionRepository
import com.pahntd.expensetracker.repository.UserRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.Uuid

class AccountService(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
) {
    /**
     * Permanently deletes the user and all data they own in a single database transaction.
     * Repository calls join this outer transaction, so any exception rolls back every delete.
     */
    fun deleteAccount(userId: Uuid) {
        transaction {
            // Locking the user row first blocks concurrent inserts that reference this user
            // (their FK check needs a share lock on it) until the deletion has committed.
            if (!userRepository.lockById(userId)) {
                throw NoSuchElementException("Account not found")
            }

            // Order matters: transactions reference categories, and neither has ON DELETE CASCADE.
            transactionRepository.deleteAllByUserId(userId)
            categoryRepository.deleteAllByUserId(userId)
            // refresh_tokens would cascade with the user, but deleting explicitly keeps intent clear.
            refreshTokenRepository.deleteAllByUserId(userId)

            if (!userRepository.delete(userId)) {
                throw NoSuchElementException("Account not found")
            }
        }
    }
}
