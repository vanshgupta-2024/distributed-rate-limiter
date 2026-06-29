# Distributed Rate Limiter - Design Document

## 1. Overview

This document outlines the architecture, design patterns, and implementation strategy for building a distributed rate limiter system using Spring Boot 4.0.6, Spring Cloud Gateway, and Redis for state management.

### Project Goals
- Implement rate limiting across multiple instances
- Support multiple rate limiting algorithms
- Provide flexible configuration per API endpoint
- Ensure low latency and high throughput
- Handle distributed scenarios with Redis as a shared state store

---

## 2. Architecture & Design

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Client Requests                         │
└────────────┬────────────────────────────────────┬────────────┘
             │                                    │
        ┌────▼────┐                          ┌────▼────┐
        │ Gateway  │                          │ Gateway  │
        │ Instance │                          │ Instance │
        │    1     │                          │    2     │
        └────┬─────┘                          └────┬─────┘
             │                                    │
             │        ┌──────────────────┐        │
             ├───────▶│  Rate Limiter    │◀───────┤
             │        │   Service        │        │
             │        └──────────┬───────┘        │
             │                   │                │
             │        ┌──────────▼───────┐        │
             └───────▶│  Redis Cluster   │◀───────┘
                      │  (Distributed)   │
                      └──────────────────┘
```

### 2.2 Core Components

1. **RateLimiter Interface** - Abstraction for different algorithms
2. **RateLimiter Implementations**:
   - Token Bucket Algorithm (recommended for distributed systems)
   - Sliding Window Log
   - Fixed Window Counter
   - Leaky Bucket
3. **RateLimitService** - Core service orchestrating rate limiting logic
4. **RedisRateLimitStore** - Redis-backed state management for distributed coordination
5. **RateLimitFilter/Interceptor** - Spring Cloud Gateway filter
6. **Configuration Management** - Dynamic rate limit rules per endpoint/user
7. **Metrics & Monitoring** - Track rate limit hits, rejections

---

## 3. Technology Stack

### Current Setup (Already in pom.xml)
- Spring Boot 4.0.6
- Spring Cloud Gateway (WebMvc)
- Spring Security
- Spring Boot Actuator

### To Be Added
- **Redis**: `spring-boot-starter-data-redis` - Distributed state store
- **Lettuce**: Redis client (comes with spring-boot-starter-data-redis)
- **Jackson**: `com.fasterxml.jackson.core:jackson-databind` - JSON serialization
- **Micrometer**: Already included via Actuator - for metrics

---

## 4. Rate Limiting Algorithms - Detailed Comparison

### 4.1 Token Bucket (RECOMMENDED)
**Best for**: Distributed systems, burst traffic handling

**How it works**:
- Tokens accumulate at a fixed rate (e.g., 100 tokens/second)
- Each request consumes N tokens
- Request rejected if insufficient tokens
- Handles bursts well

**Advantages**:
- ✅ Allows traffic bursts
- ✅ Fair to bursty workloads
- ✅ Distributed-friendly
- ✅ Easy to implement in Redis

**Implementation in Redis**:
```
Key: "rate_limit:{client_id}:{endpoint}"
Value: { tokens, last_refill_time }
```

### 4.2 Sliding Window Log
**How it works**:
- Maintains log of all requests in current window
- Accurate but memory-intensive

**Cons**: 
- ❌ Memory overhead in distributed systems
- ❌ Network overhead

### 4.3 Fixed Window Counter
**How it works**:
- Divide time into fixed windows
- Count requests per window

**Cons**:
- ❌ Spike at window boundaries
- ❌ Less fair distribution

### 4.4 Leaky Bucket
**How it works**:
- Similar to Token Bucket but deterministic
- Requests queued and processed at fixed rate

**Trade-offs**:
- Good for smoothing, but adds latency

---

## 5. Implementation Strategy

### 5.1 Package Structure
```
src/main/java/com/vansh/personal/ratelimiter/
├── RatelimiterApplication.java
├── config/
│   ├── RedisConfig.java                    # Redis connection config
│   └── GatewayConfig.java                  # Gateway route config
├── core/
│   ├── RateLimiter.java                   # Interface
│   ├── TokenBucketRateLimiter.java         # Token bucket impl
│   ├── SlidingWindowRateLimiter.java       # Sliding window impl
│   └── FixedWindowRateLimiter.java         # Fixed window impl
├── service/
│   ├── RateLimitService.java               # Main service
│   ├── RateLimitStore.java                 # Interface for state
│   └── RedisRateLimitStore.java            # Redis implementation
├── filter/
│   └── RateLimitGatewayFilter.java         # Gateway filter
├── dto/
│   ├── RateLimitConfig.java                # Configuration DTO
│   ├── RateLimitResponse.java              # Response DTO
│   └── RateLimitStatus.java                # Status DTO
├── exception/
│   └── RateLimitExceededException.java     # Custom exception
├── controller/
│   ├── RateLimitController.java            # Admin endpoints
│   └── HealthController.java               # Health check
└── util/
    └── RateLimitKeyGenerator.java          # Key generation logic
```

### 5.2 Configuration Model
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000
  gateway:
    routes:
      - id: api-route
        uri: http://backend-service
        predicates:
          - Path=/api/**

rate-limiter:
  enabled: true
  default:
    requests-per-minute: 100
    requests-per-second: 10
  rules:
    - path: /api/public
      requests-per-minute: 1000
      requests-per-second: 50
    - path: /api/premium
      requests-per-minute: 10000
      requests-per-second: 500
    - path: /api/internal
      requests-per-minute: -1  # unlimited
```

---

## 6. Step-by-Step Implementation Plan

### Phase 1: Dependencies & Configuration
1. ✅ Add Redis dependency to pom.xml
2. ✅ Create Redis configuration
3. ✅ Create application.yml with rate limit rules
4. ✅ Verify Redis connectivity

### Phase 2: Core Rate Limiter Interfaces & Models
5. ✅ Create RateLimiter interface
6. ✅ Create DTOs (RateLimitConfig, RateLimitResponse, RateLimitStatus)
7. ✅ Create custom exceptions
8. ✅ Create RateLimitKeyGenerator utility

### Phase 3: Rate Limiter Implementations
9. ✅ Implement TokenBucketRateLimiter (PRIMARY)
10. ✅ Implement SlidingWindowRateLimiter (ALTERNATIVE)
11. ✅ Implement FixedWindowRateLimiter (ALTERNATIVE)

### Phase 4: Storage Layer
12. ✅ Create RateLimitStore interface
13. ✅ Implement RedisRateLimitStore
14. ✅ Handle Redis serialization/deserialization

### Phase 5: Service Layer
15. ✅ Create RateLimitService
16. ✅ Implement request validation logic
17. ✅ Implement metrics/statistics

### Phase 6: Integration with Gateway
18. ✅ Create RateLimitGatewayFilter
19. ✅ Register filter with Spring Cloud Gateway
20. ✅ Implement response headers (X-RateLimit-*)

### Phase 7: Admin & Monitoring
21. ✅ Create RateLimitController for admin operations
22. ✅ Create endpoints for:
    - View current rate limit status
    - Update rate limit rules
    - Reset limits
    - View metrics
23. ✅ Integrate Actuator metrics

### Phase 8: Testing & Documentation
24. ✅ Unit tests for algorithms
25. ✅ Integration tests with Redis
26. ✅ Load testing scripts
27. ✅ API documentation

---

## 7. Detailed Component Specifications

### 7.1 RateLimiter Interface
```java
public interface RateLimiter {
    RateLimitResponse checkLimit(String clientId, String endpoint, RateLimitConfig config);
    void reset(String clientId, String endpoint);
    RateLimitStatus getStatus(String clientId, String endpoint);
}
```

### 7.2 RateLimitStore Interface
```java
public interface RateLimitStore {
    void saveState(String key, RateLimitState state);
    RateLimitState getState(String key);
    void delete(String key);
    void deleteExpired();
}
```

### 7.3 RateLimitService
- Orchestrates between RateLimiter and RateLimitStore
- Loads configuration rules
- Generates keys based on client + endpoint
- Returns rate limit status

### 7.4 Gateway Filter
- Extracts client identifier (IP, API key, or user)
- Calls RateLimitService.checkLimit()
- On limit exceeded: return 429 Too Many Requests
- On success: forward request and set response headers

### 7.5 Response Headers
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 45
X-RateLimit-Reset: 1234567890
X-RateLimit-Retry-After: 5
```

---

## 8. Key Design Decisions

### 8.1 Client Identification Strategy
**Options:**
1. **IP Address** - For public APIs, less accurate behind proxies
2. **API Key** - Most reliable, requires authentication
3. **User ID** - For authenticated users
4. **Hybrid** - Combine multiple (IP + API Key)

**Recommendation**: Support all, configurable per endpoint

### 8.2 Distributed Coordination
**Redis Atomic Operations:**
- Use Redis Lua scripts for atomic token bucket operations
- Ensures consistency across multiple gateway instances
- Zero race conditions

### 8.3 TTL Management
- Set expiry on Redis keys to prevent memory leak
- Configurable per endpoint (default: 1 hour)
- Use Redis `expire` with sliding window

### 8.4 Fallback Strategy
**If Redis is down:**
- Option 1: Fail open (allow all requests) - safer
- Option 2: Fail closed (reject all) - more conservative
- Configurable via properties

### 8.5 Performance Optimization
- Use Redis pipelining for batch operations
- Cache configuration in memory with TTL
- Async metrics collection
- Circuit breaker pattern for Redis failures

---

## 9. Configuration Examples

### 9.1 Per-Endpoint Configuration
```yaml
rate-limiter:
  rules:
    - id: public-api
      path-pattern: /api/public/**
      limits:
        - window: MINUTE
          requests: 1000
        - window: HOUR
          requests: 50000
    - id: premium-api
      path-pattern: /api/premium/**
      requires-auth: true
      limits:
        - window: MINUTE
          requests: 10000
```

### 9.2 Per-User Configuration
```yaml
rate-limiter:
  per-user:
    - user-id: premium_user_123
      multiplier: 10  # 10x default limits
    - user-id: admin
      unlimited: true
```

---

## 10. Monitoring & Metrics

### 10.1 Metrics to Track
- `rate.limit.requests.total` - Total requests
- `rate.limit.requests.allowed` - Allowed requests
- `rate.limit.requests.rejected` - Rejected requests
- `rate.limit.tokens.available` - Current tokens
- `rate.limit.redis.latency` - Redis operation latency
- `rate.limit.filter.duration` - Filter execution time

### 10.2 Alerts
- High rejection rate (> 10%)
- Redis unavailable
- Configuration update failures
- Latency spike in rate limit check

---

## 11. Security Considerations

1. **Prevent Bypassing**: 
   - Validate X-Forwarded-For headers
   - Use trusted proxy configuration

2. **API Key Security**:
   - Hash API keys in logs
   - Rotate keys periodically
   - Support key throttling

3. **Configuration Access**:
   - Secure admin endpoints with authentication
   - Audit all configuration changes
   - Use Spring Security

4. **Redis Security**:
   - Enable Redis authentication
   - Use encrypted connections (TLS)
   - Restrict network access

---

## 12. Testing Strategy

### 12.1 Unit Tests
- Algorithm correctness
- Edge cases (boundary times)
- Token calculation accuracy

### 12.2 Integration Tests
- Redis interaction
- Gateway filter integration
- Multiple concurrent requests

### 12.3 Distributed Tests
- Multiple gateway instances
- Redis cluster failover
- Configuration hot reload

### 12.4 Load Testing
- Throughput: X requests/second
- Latency: P99 under Y ms
- Memory usage
- Redis connection pooling

---

## 13. Deployment Considerations

### 13.1 Multi-Instance Setup
- Use Redis connection pooling
- Loadbalancer distributes traffic
- Each instance runs independent gateway
- Shared Redis cluster

### 13.2 Configuration Management
- Externalize rate limit rules to config server
- Hot reload without restart
- Version control for audit trail

### 13.3 Graceful Degradation
- Circuit breaker for Redis
- Local fallback if Redis unavailable
- Configurable behavior

---

## 14. Roadmap & Future Enhancements

### Phase 1 (MVP) - Token Bucket Algorithm
- ✅ Single algorithm implementation
- ✅ Redis backend
- ✅ Basic monitoring

### Phase 2 - Advanced Features
- Multiple algorithms selection
- Per-user multipliers
- Cost-based rate limiting (different endpoints cost different)
- Webhook notifications

### Phase 3 - Enterprise
- Rate limit queues
- Priority queuing
- SLA management
- Predictive rate limiting

### Phase 4 - Analytics
- Detailed usage analytics
- Trend analysis
- Anomaly detection
- ML-based predictions

---

## 15. Success Criteria

1. ✅ Sub-millisecond rate limit check latency
2. ✅ 99.99% accuracy in distributed scenario
3. ✅ Support for 10,000+ concurrent clients
4. ✅ Horizontal scalability (add instances)
5. ✅ Redis cluster failover support
6. ✅ Comprehensive metrics and alerting
7. ✅ Easy configuration per endpoint
8. ✅ Clear admin API for management

---

## 16. Dependencies to Add

```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Jackson for JSON -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- Micrometer for metrics (already via Actuator) -->

<!-- Testing -->
<dependency>
    <groupId>it.ozimov</groupId>
    <artifactId>embedded-redis</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 17. Next Steps

1. **Update pom.xml** with required dependencies
2. **Create Redis configuration** (RedisConfig.java)
3. **Implement core interfaces** (RateLimiter, RateLimitStore)
4. **Implement TokenBucketRateLimiter** as primary algorithm
5. **Implement RedisRateLimitStore** for persistence
6. **Create RateLimitService** orchestrator
7. **Implement RateLimitGatewayFilter** integration
8. **Create admin controller** for management
9. **Add comprehensive tests**
10. **Add configuration examples** and documentation

---

## Conclusion

This distributed rate limiter will provide:
- **Scalability**: Horizontal scaling with shared Redis backend
- **Accuracy**: Atomic operations prevent race conditions
- **Flexibility**: Pluggable algorithms and configurable rules
- **Observability**: Comprehensive metrics and monitoring
- **Resilience**: Fallback strategies and circuit breakers

The Token Bucket algorithm is recommended as the primary implementation due to its fairness, burst tolerance, and excellent fit for distributed systems.

