package com.pahntd.expensetracker

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.*

// Runs the real rootModule: requires the local PostgreSQL (docker-compose) and JWT_SECRET.
class ServerTest {

    @Test
    fun `health endpoint returns ok json`() = testApplication {
        application {
            rootModule()
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()!!.match(ContentType.Application.Json))
        assertContains(response.bodyAsText(), "\"status\": \"ok\"")
    }

    @Test
    fun `removed debug endpoints are unavailable`() = testApplication {
        application {
            rootModule()
        }

        val unavailable = setOf(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed)

        assertTrue(client.get("/").status in unavailable)
        assertTrue(client.get("/users").status in unavailable)
        assertTrue(client.post("/echo").status in unavailable)
        assertTrue(client.post("/health").status in unavailable)
    }

}
