
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
}

group = "com.pahntd.expensetracker"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "com.pahntd.expensetracker.MainKt"
    applicationDefaultJvmArgs = listOf("-Duser.timezone=UTC")
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(libs.logback.classic)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql)

    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.5.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2")

    implementation("io.ktor:ktor-server-auth-jvm:3.5.2")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.5.2")
    implementation("com.auth0:java-jwt:4.5.0")

    implementation(libs.jbcrypt)

    implementation("org.jetbrains.exposed:exposed-java-time:1.5.0")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
