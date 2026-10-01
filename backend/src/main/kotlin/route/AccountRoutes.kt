package com.pahntd.expensetracker.route

import com.pahntd.expensetracker.plugins.currentUserId
import com.pahntd.expensetracker.repository.ExposedCategoryRepository
import com.pahntd.expensetracker.repository.ExposedRefreshTokenRepository
import com.pahntd.expensetracker.repository.ExposedTransactionRepository
import com.pahntd.expensetracker.repository.ExposedUserRepository
import com.pahntd.expensetracker.service.AccountService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.route

fun Route.accountRoutes() {
    authenticate("auth-jwt") {
        route("/account") {

            delete {
                val userId = call.currentUserId()

                accountService().deleteAccount(userId)

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun accountService(): AccountService {
    return AccountService(
        userRepository = ExposedUserRepository(),
        categoryRepository = ExposedCategoryRepository(),
        transactionRepository = ExposedTransactionRepository(),
        refreshTokenRepository = ExposedRefreshTokenRepository()
    )
}
