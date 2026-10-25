# ==============================================================================
# CampusPulse — Production Multi-Stage Dockerfile
# Base: Eclipse Temurin OpenJDK 21 (Alpine Linux)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Application Artifact with Maven Wrapper
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Install bash/curl for Maven wrapper compatibility if needed
RUN apk add --no-cache bash

# Copy Maven wrapper configuration and dependencies descriptor
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make wrapper script executable and pre-fetch dependencies (layer caching)
RUN chmod +x ./mvnw && \
    ./mvnw dependency:go-offline -B || true

# Copy project source tree
COPY src/ src/

# Build production Spring Boot executable JAR (skipping test execution in image build)
RUN ./mvnw clean package -DskipTests -B && \
    cp target/campuspulse-0.0.1-SNAPSHOT.jar target/app.jar

# ------------------------------------------------------------------------------
# Stage 2: Minimal Production Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runner

WORKDIR /app

# Create unprivileged system group and user
RUN addgroup -S -g 10001 appgroup && \
    adduser -S -u 10001 -G appgroup appuser

# Copy executable jar from builder stage with correct ownership
COPY --from=builder --chown=appuser:appgroup /workspace/target/app.jar app.jar

# Configure JVM container options and default listening port
ENV PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Switch to unprivileged runtime user
USER appuser

# Expose default application port (can be overridden by cloud platform via $PORT)
EXPOSE 8080

# Health check using the lightweight liveness probe
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget -q -O - http://127.0.0.1:${PORT:-8080}/health/live || exit 1

# Start Spring Boot application honoring platform $PORT and $JAVA_OPTS
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
