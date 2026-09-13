package com.pahntd.expensetracker.plugins

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import kotlin.uuid.Uuid

// Safe only inside authenticate("auth-jwt") { }, which guarantees a principal with a userId claim.
fun ApplicationCall.currentUserId(): Uuid {
    val principal = principal<JWTPrincipal>()!!
    val userIdClaim = principal.payload.getClaim("userId").asString()!!
    return Uuid.parse(userIdClaim)
}
