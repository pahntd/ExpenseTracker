# syntax=docker/dockerfile:1

# ---- Build stage: compile the self-contained fat JAR with the repo's Gradle wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Build scripts first so the dependency download layer is cached across source edits.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon buildFatJar -x test

# ---- Runtime stage: JRE only, non-root, no configuration or secrets baked in ----
FROM eclipse-temurin:21-jre

RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /workspace/build/libs/backend-all.jar /app/backend.jar
USER app

# UTC at the OS level and for the JVM (main() also forces UTC as a last line of defence).
ENV TZ=UTC

# Images are for deployment: default to production so missing DB variables fail fast instead of
# falling back to the localhost development database. Override with APP_ENV=development if needed.
ENV APP_ENV=production

# The server binds 0.0.0.0:${PORT:-8080}. DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD and
# JWT_SECRET must be supplied at runtime.
EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/backend.jar"]
