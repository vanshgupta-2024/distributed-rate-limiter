# Multi-stage build for Spring Boot Distributed Rate Limiter

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 as builder

WORKDIR /build

# Copy pom.xml and download dependencies (layer caching)
COPY pom.xml .
RUN mvn dependency:resolve

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the JAR from builder stage
COPY --from=builder /build/target/ratelimiter-*.jar app.jar

# Create a non-root user
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser appuser && \
    chown -R appuser:appuser /app

USER appuser

# Expose port
EXPOSE 8080

# Spring Boot health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/rate-limiter/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

