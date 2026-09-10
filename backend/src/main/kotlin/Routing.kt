package com.pahntd.expensetracker

import com.pahntd.expensetracker.api.EchoRequest
import com.pahntd.expensetracker.api.EchoResponse
import com.pahntd.expensetracker.api.ErrorResponse
import com.pahntd.expensetracker.api.HealthResponse
import com.pahntd.expensetracker.api.LoginRequest
import com.pahntd.expensetracker.api.LoginResponse
import com.pahntd.expensetracker.api.LogoutRequest
import com.pahntd.expensetracker.api.RefreshTokenRequest
import com.pahntd.expensetracker.api.RefreshTokenResponse
import com.pahntd.expensetracker.api.RegisterRequest
import com.pahntd.expensetracker.api.RegisterResponse
import com.pahntd.expensetracker.api.UserResponse
import com.pahntd.expensetracker.auth.BCryptPasswordHasher
import com.pahntd.expensetracker.auth.JwtConfig
import com.pahntd.expensetracker.auth.RefreshTokenGenerator
import com.pahntd.expensetracker.auth.TokenService
import com.pahntd.expensetracker.model.RefreshToken
import com.pahntd.expensetracker.repository.ExposedRefreshTokenRepository
import com.pahntd.expensetracker.repository.ExposedUserRepository
import com.pahntd.expensetracker.repository.RefreshTokenRepository
import com.pahntd.expensetracker.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.OffsetDateTime
import java.time.OffsetDateTime.now
import java.time.ZoneOffset
import kotlin.uuid.Uuid

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }

        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok"
                )
            )
        }

        post("/health") {
            call.respond(
                HealthResponse(
                    status = "OK"
                )
            )
        }

        post("/echo") {

            val request = call.receive<EchoRequest>()

            call.respond(
                EchoResponse(
                    message = request.message
                )
            )
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()

            val passwordHasher = BCryptPasswordHasher()

            val userRepository = ExposedUserRepository()

            val authService = AuthService(
                userRepository = userRepository,
                passwordHasher = passwordHasher
            )

            val user = authService.register(
                email = request.email,
                password = request.password
            )

            call.respond(
                HttpStatusCode.Created,
                RegisterResponse(
                    id = user.id.toString(),
                    email = user.email
                )
            )

        }
        authenticate("auth-jwt") {
            get("/users") {
                val userRepository = ExposedUserRepository()
                val users = userRepository.findAll()
                call.respond(
                    users.map { user ->
                        UserResponse(
                            id = user.id.toString(),
                            email = user.email,
                            passwordHash = user.passwordHash
                        )
                    }
                )
            }
        }
        /*
          "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJleHBlbnNlLXRyYWNrZXIiLCJhdWQiOiJleHBlbnNlLXRyYWNrZXItYXBwIiwidXNlcklkIjoiY2YxMDFiODMtNTI1ZC00YTNkLWFiZWEtY2ZlY2ZkZDQ2ZDI3IiwiaWF0IjoxNzg4ODg4MDMyLCJleHAiOjE3ODg4ODg5MzJ9.RurmK9W_iwvkzKkq-x0l2dkRzeq1d4bbsVU6H6f3seo",
    "refreshToken": "Bwc2cfilio_PQlnautO68-_31ntBW1w3I2lmdgTeJ1M"
        * */

        post("/login") {
            val request = call.receive<LoginRequest>()
            val passwordHasher = BCryptPasswordHasher()
            val userRepository = ExposedUserRepository()
            val authService = AuthService(
                userRepository = userRepository,
                passwordHasher = passwordHasher
            )
            val user = authService.login(
                email = request.email,
                password = request.password
            )

            val tokenService = TokenService()


            val refreshTokenRepository =
                ExposedRefreshTokenRepository()

            val refreshTokenGenerator =
                RefreshTokenGenerator()

            val accessToken = tokenService.generateAccessToken(
                user.id
            )

            val refreshToken =
                refreshTokenGenerator.generate()


            val refreshTokenEntity = RefreshToken(
                id = Uuid.random(),
                userId = user.id,
                token = refreshToken,
                expiresAt = now().plusDays(
                    JwtConfig.refreshTokenExpirationDays
                ),
                createdAt = now(),
                revokedAt = null
            )

            refreshTokenRepository.create(
                refreshTokenEntity
            )

            call.respond(
                LoginResponse(
                    userId = user.id.toString(),
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            )
        }

        post("/auth/refresh"){
            val request = call.receive<RefreshTokenRequest>()
            val refreshTokenRepository = ExposedRefreshTokenRepository()
            val refreshToken = refreshTokenRepository.findByToken(
                request.refreshToken
            )
            if (refreshToken == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    "Invalid refresh token"
                )
                return@post
            }
            if (refreshToken.revokedAt != null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    "Refresh token has been revoked"
                )
                return@post
            }
            if (refreshToken.expiresAt.isBefore(now(ZoneOffset.UTC)
                )
            ) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    "Refresh token has expired"
                )
                return@post
            }
            val tokenService = TokenService()
            val accessToken = tokenService.generateAccessToken(
                refreshToken.userId
            )
            call.respond(
                RefreshTokenResponse(
                    accessToken = accessToken
                )
            )
        }

        post("/auth/logout"){
            val request = call.receive<LogoutRequest>()
            val refreshTokenRepository = ExposedRefreshTokenRepository()
            val refreshToken = refreshTokenRepository.findByToken(
                request.refreshToken
            )

            if (refreshToken == null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Invalid refresh token")
                )
                return@post
            }

            if (refreshToken.revokedAt != null) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Refresh token has already been revoked")
                )
                return@post
            }
            if (refreshToken.expiresAt.isBefore(now(ZoneOffset.UTC)
                )
            ) {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    "Refresh token has expired"
                )
                return@post
            }
            refreshTokenRepository.revoke(
                request.refreshToken
            )

            call.respond(
                HttpStatusCode.NoContent
            )
        }
    }
}