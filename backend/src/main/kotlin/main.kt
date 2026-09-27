package com.pahntd.expensetracker

import com.pahntd.expensetracker.config.ServerConfig
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import java.util.TimeZone

fun main(args: Array<String>) {
    // Force UTC before anything reads the default zone (JDBC connections, java.time "now").
    // The -Duser.timezone=UTC Gradle arg does not apply to `java -jar`.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = ServerConfig.port(System.getenv("PORT")),
        host = ServerConfig.HOST,
        module = Application::rootModule
    ).start(wait = true)
}
