package com.pahntd.expensetracker.route

import com.pahntd.expensetracker.api.ErrorResponse
import com.pahntd.expensetracker.api.HealthResponse
import com.pahntd.expensetracker.api.LoginRequest
import com.pahntd.expensetracker.api.LoginResponse
import com.pahntd.expensetracker.api.LogoutRequest
import com.pahntd.expensetracker.api.RefreshTokenRequest
import com.pahntd.expensetracker.api.RefreshTokenResponse
import com.pahntd.expensetracker.api.RegisterRequest
import com.pahntd.expensetracker.api.RegisterResponse
import com.pahntd.expensetracker.auth.BCryptPasswordHasher
import com.pahntd.expensetracker.auth.JwtConfig
import com.pahntd.expensetracker.auth.RefreshTokenGenerator
import com.pahntd.expensetracker.auth.TokenService
import com.pahntd.expensetracker.model.RefreshToken
import com.pahntd.expensetracker.repository.ExposedCategoryRepository
import com.pahntd.expensetracker.repository.ExposedRefreshTokenRepository
import com.pahntd.expensetracker.repository.ExposedUserRepository
import com.pahntd.expensetracker.service.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.OffsetDateTime.now
import java.time.ZoneOffset
import kotlin.uuid.Uuid

fun Application.configureRouting() {
    routing {
        categoryRoutes()
        transactionRoutes()
        accountRoutes()

        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok"
                )
            )
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()

            val passwordHasher = BCryptPasswordHasher()

            val userRepository = ExposedUserRepository()
            val categoryRepository = ExposedCategoryRepository()

            val authService = AuthService(
                userRepository = userRepository,
                categoryRepository = categoryRepository,
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

        post("/login") {
            val request = call.receive<LoginRequest>()
            val passwordHasher = BCryptPasswordHasher()
            val userRepository = ExposedUserRepository()
            val categoryRepository = ExposedCategoryRepository()
            val authService = AuthService(
                userRepository = userRepository,
                categoryRepository = categoryRepository,
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
                    email = user.email,
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