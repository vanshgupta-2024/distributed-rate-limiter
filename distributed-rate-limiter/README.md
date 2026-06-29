# Distributed Rate Limiter — Local Implementation

This repository contains a runnable Spring Boot application implementing a simple distributed-style rate limiter. It follows the project's `IMPLEMENTATION_GUIDE.md` and provides an in-memory token-bucket implementation (easy to replace with Redis-backed state).

What I implemented

- `RateLimitConfig` and `RateLimitStatus` DTOs
- `RateLimiterService` — an in-memory token-bucket store and configuration registry
- Admin REST endpoints under `/api/rate-limiter` for status, reset, config management and health
- Public test controller at `/api/public/test`
- `RateLimitServletFilter` — a servlet filter that applies rate limiting to `/api/**` paths
- Simple security configuration changed to permit all requests for local development; Spring Security auto-configuration exclusion is set in `application.yml`
- `application.yml` contains default config and Spring settings

Files added or updated

- src/main/java/com/vansh/personal/ratelimiter/model/RateLimitConfig.java
- src/main/java/com/vansh/personal/ratelimiter/model/RateLimitStatus.java
- src/main/java/com/vansh/personal/ratelimiter/service/RateLimiterService.java
- src/main/java/com/vansh/personal/ratelimiter/controller/RateLimiterAdminController.java
- src/main/java/com/vansh/personal/ratelimiter/controller/PublicApiController.java
- src/main/java/com/vansh/personal/ratelimiter/filter/RateLimitServletFilter.java
- src/main/java/com/vansh/personal/ratelimiter/filter/RateLimitWebFilter.java (left in place for potential WebFlux use)
- src/main/java/com/vansh/personal/ratelimiter/config/SecurityConfig.java
- src/main/resources/application.yml (updated to exclude Security auto-config)

Quick start (macOS / zsh)

Prerequisites:
- Java 21+ (the project was compiled with a later JDK on this machine)
- Maven 3.8+
- (Optional) Redis if you later want to switch to a Redis-backed implementation

Build:

```bash
cd /Users/vanshgupta/Desktop/projects/distributed-rate-limiter
./mvnw -DskipTests package
```

Run:

```bash
# start the built jar
java -jar target/ratelimiter-0.0.1-SNAPSHOT.jar &
```

Or using Maven:

```bash
./mvnw spring-boot:run
```

API examples

- Public test:

```bash
curl -i http://localhost:8080/api/public/test
```

- Check rate limiter status:

```bash
curl "http://localhost:8080/api/rate-limiter/status?clientId=apiKey:test-key&endpoint=/api/public/test"
```

- Register a new config:

```bash
curl -X POST "http://localhost:8080/api/rate-limiter/config" \
  -H "Content-Type: application/json" \
  -d '{"id":"custom-api","pathPattern":"/api/custom/**","requestsPerSecond":2,"requestsPerMinute":120,"ttlSeconds":3600,"requiresAuth":false,"description":"Custom API","enabled":true,"priority":105}'
```

- Match config for a path:

```bash
curl "http://localhost:8080/api/rate-limiter/config/match?path=/api/custom/foo"
```

Notes & next steps

- The current implementation uses an in-memory token-bucket per client+endpoint. It is suitable for local development and demos but not for multi-instance deployments.
- To make this truly distributed, replace the in-memory `TokenBucket` state with Redis-based storage (use atomic Lua scripts or Redis tokens/counters) — the `RateLimiterService` is the place to swap that implementation.
- The application excludes Spring Security's default auto-configuration to simplify local testing. Do not use this configuration in production.

If you want, I can now:
- Replace the in-memory store with a Redis-backed implementation (using Spring Data Redis and Lua scripts)
- Add unit and integration tests
- Add Docker Compose to launch Redis + the app for easy local testing

Redis-backed distributed mode
----------------------------

I added a Redis-backed implementation that uses atomic Lua scripts to perform token-bucket operations in Redis. Files added:

- src/main/resources/lua/token-bucket.lua — atomic consume script
- src/main/resources/lua/get-status.lua — read-only status script
- src/main/java/com/vansh/personal/ratelimiter/service/RedisRateLimiterService.java — Redis-backed service (active when `spring.profiles.active=redis`)
- src/main/java/com/vansh/personal/ratelimiter/service/IRateLimiterService.java — common interface
- src/main/java/com/vansh/personal/ratelimiter/config/RedisRateLimiterConfig.java — RedisTemplate bean for `redis` profile
- Dockerfile, docker-compose.yml — to run app + Redis locally

Run locally with Docker Compose (recommended):

```bash
# from project root
docker compose up --build
```

This will start Redis and the app with the `redis` profile enabled (see `docker-compose.yml`).

Run locally without Docker (connect to a running Redis instance):

```bash
# start app with redis profile and point to your redis
SPRING_PROFILES_ACTIVE=redis REDIS_HOST=localhost REDIS_PORT=6379 ./mvnw spring-boot:run
```

Notes about tests
-----------------
An integration test that exercises the Redis-backed service was added (`src/test/java/.../RedisRateLimiterIntegrationTest.java`). It attempts to run against Docker (Testcontainers) if Docker is available; otherwise the test is skipped on machines without Docker.


