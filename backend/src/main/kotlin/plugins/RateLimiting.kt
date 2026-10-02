package com.pahntd.expensetracker.plugins

import com.pahntd.expensetracker.api.ErrorResponse
import com.pahntd.expensetracker.api.RateLimitErrorResponse
import com.pahntd.expensetracker.ratelimit.AccountLoginLimiter
import com.pahntd.expensetracker.ratelimit.ClientIpResolver
import com.pahntd.expensetracker.ratelimit.RateLimitDecision
import com.pahntd.expensetracker.ratelimit.RateLimitPolicy
import com.pahntd.expensetracker.ratelimit.RateLimitService
import com.pahntd.expensetracker.ratelimit.RateLimitStoreUnavailableException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.Hook
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.isHandled
import io.ktor.server.application.log
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.application
import io.ktor.util.AttributeKey

/**
 * Redis-backed IP rate limiting for selected routes.
 *
 * Ktor's RateLimit plugin is not used: it keeps one limiter object per key in an in-memory map
 * (its tryConsume() does not receive the key) and answers 429 without a body. Here the state lives
 * only in Redis, so every backend instance shares the same counters.
 */
class RateLimiting(
    val service: RateLimitService,
    val clientIpResolver: ClientIpResolver
) {
    /** Per-account login protection; shares [service] and therefore the one Redis connection. */
    val accountLoginLimiter = AccountLoginLimiter(service)
}

private val RateLimitingKey = AttributeKey<RateLimiting>("RateLimiting")

fun Application.configureRateLimiting(service: RateLimitService, clientIpResolver: ClientIpResolver) {
    attributes.put(RateLimitingKey, RateLimiting(service, clientIpResolver))
    log.info(
        "IP rate limiting enabled; client IP from " +
            (clientIpResolver.trustedHeader?.let { "header $it" } ?: "socket peer address")
    )
}

val Application.accountLoginLimiter: AccountLoginLimiter
    get() = rateLimiting().accountLoginLimiter

private fun Application.rateLimiting(): RateLimiting =
    attributes.getOrNull(RateLimitingKey)
        ?: throw IllegalStateException("configureRateLimiting() must run before routing is configured")

/**
 * Applies [policy] to the routes built in [build]. The check runs before the route handler, so a
 * limited request never reaches business logic (e.g. no password verification on /login).
 */
fun Route.rateLimitByIp(policy: RateLimitPolicy, build: Route.() -> Unit): Route {
    val rateLimiting = application.rateLimiting()

    val route = createChild(RateLimitRouteSelector(policy))
    route.install(IpRateLimitInterceptor) {
        this.policy = policy
        this.rateLimiting = rateLimiting
    }
    route.build()
    return route
}

private class IpRateLimitConfig {
    lateinit var policy: RateLimitPolicy
    lateinit var rateLimiting: RateLimiting
}

private val IpRateLimitInterceptor = createRouteScopedPlugin("IpRateLimit", ::IpRateLimitConfig) {
    val policy = pluginConfig.policy
    val service = pluginConfig.rateLimiting.service
    val clientIpResolver = pluginConfig.rateLimiting.clientIpResolver

    on(BeforeRouteHandler) { call ->
        val clientIp = clientIpResolver.resolve(call)

        val decision = try {
            service.tryConsume(policy, clientIp)
        } catch (e: RateLimitStoreUnavailableException) {
            call.respondRateLimitUnavailable(policy, e)
            return@on
        }

        if (decision is RateLimitDecision.Limited) {
            call.respondRateLimited(decision.retryAfterSeconds)
        }
    }
}

/** The one 429 contract shared by every limiter: Retry-After header + [RateLimitErrorResponse]. */
suspend fun ApplicationCall.respondRateLimited(retryAfterSeconds: Long) {
    response.header(HttpHeaders.RetryAfter, retryAfterSeconds)
    respond(
        HttpStatusCode.TooManyRequests,
        RateLimitErrorResponse(
            error = "Too many requests. Please try again later.",
            code = "RATE_LIMITED",
            retryAfterSeconds = retryAfterSeconds
        )
    )
}

/**
 * Fail closed: without Redis a limit cannot be enforced, and silently skipping it would turn the
 * protection off. Only the rate-limited auth endpoints are affected.
 */
suspend fun ApplicationCall.respondRateLimitUnavailable(
    policy: RateLimitPolicy,
    cause: RateLimitStoreUnavailableException
) {
    application.log.error("Rate limit check failed for ${policy.name}", cause)
    respond(
        HttpStatusCode.ServiceUnavailable,
        ErrorResponse("Service temporarily unavailable. Please try again later.")
    )
}

/** Runs in the route's Plugins phase, before the handler; stops the pipeline once responded. */
private object BeforeRouteHandler : Hook<suspend (ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: suspend (ApplicationCall) -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Plugins) {
            handler(call)
            if (call.isHandled) {
                finish()
            }
        }
    }
}

private class RateLimitRouteSelector(val policy: RateLimitPolicy) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(RateLimitByIp ${policy.bucket})"
}
