# -------------------------------------------------------
# Stage 1: Build
# -------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Gradle wrapper and build files first (cache dependencies)
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Download dependencies (cached layer)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src/ src/

# Build the application (skip tests — run separately in CI)
RUN ./gradlew build -x test --no-daemon

# -------------------------------------------------------
# Stage 2: Runtime
# -------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S cce && adduser -S cce -G cce

WORKDIR /app

COPY --from=builder /app/build/libs/cce-collector-service-*.jar app.jar

RUN chown -R cce:cce /app
USER cce

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
