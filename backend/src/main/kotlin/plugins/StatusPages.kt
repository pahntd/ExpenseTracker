package com.pahntd.expensetracker.plugins

import com.pahntd.expensetracker.api.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {

    install(StatusPages) {

        exception<IllegalArgumentException> { call, cause ->

            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponse(
                    error = cause.message ?: "Invalid request"
                )
            )
        }

        exception<NoSuchElementException> { call, cause ->

            call.respond(
                status = HttpStatusCode.NotFound,
                message = ErrorResponse(
                    error = cause.message ?: "Resource not found"
                )
            )
        }

        exception<IllegalStateException> { call, cause ->

            call.respond(
                status = HttpStatusCode.Conflict,
                message = ErrorResponse(
                    error = cause.message ?: "Request conflicts with current state"
                )
            )
        }
    }
}