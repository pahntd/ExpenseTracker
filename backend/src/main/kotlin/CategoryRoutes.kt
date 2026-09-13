package com.pahntd.expensetracker

import com.pahntd.expensetracker.api.CreateCategoryRequest
import com.pahntd.expensetracker.api.UpdateCategoryRequest
import com.pahntd.expensetracker.api.toResponse
import com.pahntd.expensetracker.plugins.currentUserId
import com.pahntd.expensetracker.repository.ExposedCategoryRepository
import com.pahntd.expensetracker.repository.ExposedTransactionRepository
import com.pahntd.expensetracker.service.CategoryService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.categoryRoutes() {
    authenticate("auth-jwt") {
        route("/categories") {

            get {
                val userId = call.currentUserId()
                val categories = categoryService().getAll(userId)
                call.respond(categories.map { it.toResponse() })
            }

            post {
                val userId = call.currentUserId()
                val request = call.receive<CreateCategoryRequest>()

                val category = categoryService().create(
                    userId = userId,
                    name = request.name,
                    icon = request.icon
                )

                call.respond(HttpStatusCode.Created, category.toResponse())
            }

            put("/{id}") {
                val id = parsePathUuid(call.parameters["id"])
                val userId = call.currentUserId()
                val request = call.receive<UpdateCategoryRequest>()

                val category = categoryService().update(
                    userId = userId,
                    id = id,
                    name = request.name,
                    icon = request.icon
                )

                call.respond(category.toResponse())
            }

            delete("/{id}") {
                val id = parsePathUuid(call.parameters["id"])
                val userId = call.currentUserId()

                categoryService().delete(userId = userId, id = id)

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun categoryService(): CategoryService {
    return CategoryService(
        categoryRepository = ExposedCategoryRepository(),
        transactionRepository = ExposedTransactionRepository()
    )
}
