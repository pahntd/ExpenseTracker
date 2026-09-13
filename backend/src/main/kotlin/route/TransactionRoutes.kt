package com.pahntd.expensetracker.route

import com.pahntd.expensetracker.api.CreateTransactionRequest
import com.pahntd.expensetracker.api.UpdateTransactionRequest
import com.pahntd.expensetracker.api.toResponse
import com.pahntd.expensetracker.plugins.currentUserId
import com.pahntd.expensetracker.repository.ExposedCategoryRepository
import com.pahntd.expensetracker.repository.ExposedTransactionRepository
import com.pahntd.expensetracker.service.TransactionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.transactionRoutes() {
    authenticate("auth-jwt") {
        route("/transactions") {

            get {
                val userId = call.currentUserId()
                val transactions = transactionService().getAll(userId)
                call.respond(transactions.map { it.toResponse() })
            }

            post {
                val userId = call.currentUserId()
                val request = call.receive<CreateTransactionRequest>()

                val transaction = transactionService().create(
                    userId = userId,
                    amount = parseAmount(request.amount),
                    type = parseTransactionType(request.type),
                    categoryId = parseOptionalUuid(request.categoryId),
                    date = parseOffsetDateTime(request.date),
                    title = request.title
                )

                call.respond(HttpStatusCode.Created, transaction.toResponse())
            }

            put("/{id}") {
                val id = parsePathUuid(call.parameters["id"])
                val userId = call.currentUserId()
                val request = call.receive<UpdateTransactionRequest>()

                val transaction = transactionService().update(
                    userId = userId,
                    id = id,
                    amount = parseAmount(request.amount),
                    type = parseTransactionType(request.type),
                    categoryId = parseOptionalUuid(request.categoryId),
                    date = parseOffsetDateTime(request.date),
                    title = request.title
                )

                call.respond(transaction.toResponse())
            }

            delete("/{id}") {
                val id = parsePathUuid(call.parameters["id"])
                val userId = call.currentUserId()

                transactionService().delete(userId = userId, id = id)

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun transactionService(): TransactionService {
    return TransactionService(
        transactionRepository = ExposedTransactionRepository(),
        categoryRepository = ExposedCategoryRepository()
    )
}
