package com.pahntd.expensetracker.ratelimit

import io.ktor.server.application.ApplicationCall
import org.slf4j.LoggerFactory

/**
 * Determines the client IP used as the rate-limit key.
 *
 * By default only the TCP peer address is used; no request header is trusted, so a client cannot
 * pick its own bucket. Behind a reverse proxy every request comes from the proxy, so the
 * deployment names, via CLIENT_IP_HEADER, the single header its proxy overwrites with the real
 * client address.
 *
 * Render: requests pass through Cloudflare, which overwrites `CF-Connecting-IP` with the connecting
 * client's address, then reach the app from an internal 10.x proxy address. X-Forwarded-For is NOT
 * used: Render appends to it, so its first entry is whatever the client sent.
 * Set CLIENT_IP_HEADER=CF-Connecting-IP there. Never set it on a server reachable without that proxy.
 */
class ClientIpResolver(
    val trustedHeader: String?
) {
    fun resolve(call: ApplicationCall): String {
        // local.remoteAddress is always the socket peer, even if a ForwardedHeaders plugin is added.
        return resolve(
            headerValue = trustedHeader?.let { call.request.headers[it] },
            remoteAddress = call.request.local.remoteAddress
        )
    }

    fun resolve(headerValue: String?, remoteAddress: String): String {
        if (trustedHeader == null) {
            return remoteAddress
        }

        val candidate = headerValue?.trim()?.lowercase()
        if (candidate != null && isIpLiteral(candidate)) {
            return candidate
        }

        // Falls back to the proxy address: stricter (shared bucket), never looser.
        logger.warn(
            "Header {} is missing or not an IP address; rate limiting by peer address {}",
            trustedHeader,
            remoteAddress
        )
        return remoteAddress
    }

    // Cheap shape check, no DNS lookup: the value goes into a Redis key, so reject anything else.
    private fun isIpLiteral(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_IP_LENGTH) return false
        return IPV4.matches(value) || (value.contains(':') && IPV6_CHARS.matches(value))
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClientIpResolver::class.java)

        private const val MAX_IP_LENGTH = 45
        private val IPV4 = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        private val IPV6_CHARS = Regex("""^[0-9a-f:.]+$""")

        /** Reads CLIENT_IP_HEADER; unset/blank means "trust no header". */
        fun fromEnvironment(getenv: (String) -> String? = System::getenv): ClientIpResolver {
            return ClientIpResolver(getenv("CLIENT_IP_HEADER")?.trim()?.takeIf { it.isNotBlank() })
        }
    }
}
