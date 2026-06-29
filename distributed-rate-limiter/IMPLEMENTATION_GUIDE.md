# Implementation Guide & Usage

## Quick Start

### 1. Prerequisites
- Java 21+
- Redis running locally (port 6379) or configured in application.yml
- Maven 3.8+

### 2. Start Redis
```bash
# Using Docker
docker run -d -p 6379:6379 redis:latest

# Or if Redis is installed locally
redis-server
```

### 3. Build and Run
```bash
cd distributed-rate-limiter
mvn clean install
mvn spring-boot:run
```

The application will start on http://localhost:8080

---

## API Endpoints

### Admin Endpoints (Rate Limiter Management)

#### 1. Check Rate Limit Status
```bash
curl -X GET "http://localhost:8080/api/rate-limiter/status?clientId=user123&endpoint=/api/users" \
  -H "Content-Type: application/json"
```

Response:
```json
{
  "client_id": "user123",
  "endpoint": "/api/users",
  "tokens_available": 8.5,
  "tokens_max": 10.0,
  "refill_rate": 10.0,
  "last_refill_time": 1683720000000,
  "total_requests": 2,
  "allowed_requests": 2,
  "rejected_requests": 0,
  "is_active": true,
  "timestamp": 1683720001000
}
```

#### 2. Reset Rate Limit
```bash
curl -X POST "http://localhost:8080/api/rate-limiter/reset?clientId=user123&endpoint=/api/users"
```

Response:
```json
{
  "message": "Rate limit reset successfully",
  "clientId": "user123",
  "endpoint": "/api/users"
}
```

#### 3. Register New Rate Limit Configuration
```bash
curl -X POST "http://localhost:8080/api/rate-limiter/config" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "custom-api",
    "pathPattern": "/api/custom/**",
    "requestsPerSecond": 20,
    "requestsPerMinute": 1200,
    "ttlSeconds": 3600,
    "requiresAuth": false,
    "description": "Custom API rate limit",
    "enabled": true,
    "priority": 105
  }'
```

#### 4. Get Specific Configuration
```bash
curl -X GET "http://localhost:8080/api/rate-limiter/config/%2Fapi%2Fpublic%2F%2A%2A"
```

#### 5. Get All Configurations
```bash
curl -X GET "http://localhost:8080/api/rate-limiter/configs"
```

#### 6. Find Matching Configuration for Path
```bash
curl -X GET "http://localhost:8080/api/rate-limiter/config/match?path=/api/public/users"
```

#### 7. Health Check
```bash
curl -X GET "http://localhost:8080/api/rate-limiter/health"
```

Response:
```json
{
  "status": "UP",
  "service": "rate-limiter",
  "timestamp": "1683720001000"
}
```

---

## Rate Limit Response Headers

Every response includes rate limit information in headers:

```
X-RateLimit-Limit: 100              # Total limit
X-RateLimit-Remaining: 45           # Requests remaining
X-RateLimit-Reset: 1683720000       # Unix timestamp when limit resets
Retry-After: 5                       # Seconds to wait (if rejected)
```

---

## Rate Limit Exceeded Response

When rate limit is exceeded, you get a 429 response:

```json
{
  "error": "Too Many Requests",
  "message": "Rate limit exceeded",
  "retry_after_seconds": 5
}
```

---

## Configuration Examples

### Example 1: Public API with Moderate Limits
```yaml
rate-limiter:
  rules:
    - id: public-api
      pathPattern: /api/public/**
      requestsPerSecond: 10
      requestsPerMinute: 600
      ttlSeconds: 3600
      requiresAuth: false
      enabled: true
      priority: 100
```

### Example 2: Premium Tier with Higher Limits
```yaml
rate-limiter:
  rules:
    - id: premium-api
      pathPattern: /api/premium/**
      requestsPerSecond: 50
      requestsPerMinute: 3000
      ttlSeconds: 3600
      requiresAuth: true
      enabled: true
      priority: 110
```

### Example 3: Internal API (No Limits)
```yaml
rate-limiter:
  rules:
    - id: internal-api
      pathPattern: /api/internal/**
      requestsPerSecond: 10000
      requestsPerMinute: 600000
      ttlSeconds: 3600
      requiresAuth: true
      enabled: true
      priority: 90
```

---

## Client Identification

The system automatically identifies clients through multiple strategies:

### Priority Order:
1. **X-API-Key header** - Best for API clients
   ```bash
   curl -H "X-API-Key: my-api-key-123" http://localhost:8080/api/endpoint
   ```

2. **Authorization header** - For authenticated users
   ```bash
   curl -H "Authorization: Bearer token123" http://localhost:8080/api/endpoint
   ```

3. **X-Forwarded-For header** - For requests through proxies
   ```bash
   curl -H "X-Forwarded-For: 192.168.1.100" http://localhost:8080/api/endpoint
   ```

4. **Remote address** - Fallback to client IP address

---

## Testing with curl

### Test 1: Verify Rate Limiting Works
```bash
#!/bin/bash

# Make 15 rapid requests to the public API
for i in {1..15}; do
  echo "Request $i:"
  curl -w "\nStatus: %{http_code}\n" \
    -H "X-API-Key: test-key" \
    "http://localhost:8080/api/public/test"
  echo ""
  sleep 0.1
done
```

Expected: First ~10 requests succeed, remaining fail with 429

### Test 2: Verify Token Refill
```bash
# Make 5 requests
curl -H "X-API-Key: test-key-2" "http://localhost:8080/api/public/test"
curl -H "X-API-Key: test-key-2" "http://localhost:8080/api/public/test"
curl -H "X-API-Key: test-key-2" "http://localhost:8080/api/public/test"
curl -H "X-API-Key: test-key-2" "http://localhost:8080/api/public/test"
curl -H "X-API-Key: test-key-2" "http://localhost:8080/api/public/test"

# Wait 1 second
sleep 1

# This should succeed (tokens refilled)
curl -H "X-API-Key: test-key-2" "http://localhost:8080/api/public/test"
```

### Test 3: Check Status
```bash
curl "http://localhost:8080/api/rate-limiter/status?clientId=ip:127.0.0.1&endpoint=/api/public/test"
```

---

## Configuration File (application.yml)

The default configuration includes:

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 10
        max-idle: 5
        min-idle: 1

rate-limiter:
  enabled: true
  default:
    requests-per-second: 10
    requests-per-minute: 100
```

### Customize for Your Environment:

```yaml
spring:
  redis:
    host: redis.example.com
    port: 6380
    password: your-redis-password
    ssl: true

rate-limiter:
  enabled: true
```

---

## Monitoring & Metrics

### View Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

### Prometheus Metrics
```bash
curl http://localhost:8080/actuator/prometheus
```

---

## Troubleshooting

### Issue: Redis Connection Refused
**Solution:** Ensure Redis is running
```bash
docker run -d -p 6379:6379 redis:latest
```

### Issue: Rate Limiter Not Working
**Solution:** Check if enabled in application.yml
```yaml
rate-limiter:
  enabled: true  # Must be true
```

### Issue: Requests Not Being Limited
**Solution:** Verify configuration is registered
```bash
curl http://localhost:8080/api/rate-limiter/configs
```

### Issue: Status Shows "is_active: false"
**Solution:** No requests have been made for this client+endpoint combination yet

---

## Performance Tuning

### Optimize Redis Connection Pool
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20       # Increase for high traffic
        max-idle: 10
        min-idle: 5
        max-wait: -1ms
```

### Optimize Rate Limiter
- Use appropriate `requestsPerSecond` - too high defeats purpose
- Set reasonable TTL - longer means better memory usage
- Configure appropriate priority - more specific patterns have higher priority

---

## Integration with Your Services

### Behind a Load Balancer
Ensure X-Forwarded-For header is properly configured:
```yaml
server:
  forward-headers-strategy: framework
```

### Multi-Gateway Setup
All gateways share same Redis instance - truly distributed!

```
Gateway 1 ─┐
           ├─ Redis (Shared State)
Gateway 2 ─┘
```

---

## Next Steps

1. **Customize Configurations** - Add your API endpoints to rate limit rules
2. **Set Up Monitoring** - Configure Prometheus/Grafana for metrics
3. **Deploy to Production** - Use managed Redis service (AWS ElastiCache, etc.)
4. **Load Test** - Use tools like Apache JMeter or Gatling
5. **Monitor Performance** - Check latency and error rates

---

## Common Use Cases

### Scenario 1: API with Free and Premium Tiers
```yaml
rate-limiter:
  rules:
    - pathPattern: /api/free/**
      requestsPerSecond: 1
      requestsPerMinute: 100
      priority: 100
    - pathPattern: /api/premium/**
      requestsPerSecond: 100
      requestsPerMinute: 10000
      priority: 110
```

### Scenario 2: Search API with Different Limits per Endpoint
```yaml
rate-limiter:
  rules:
    - pathPattern: /api/search/simple
      requestsPerSecond: 50
      priority: 100
    - pathPattern: /api/search/advanced
      requestsPerSecond: 10
      priority: 110
```

### Scenario 3: Webhook Endpoints with Burst Allowance
```yaml
rate-limiter:
  rules:
    - pathPattern: /webhook/**
      requestsPerSecond: 5
      # Token bucket allows bursts up to 10 (2x per second rate)
      priority: 100
```

---

## Support & Documentation

- **Design Doc**: See DESIGN_DOC.md for architecture details
- **API Documentation**: Available at http://localhost:8080/api/rate-limiter
- **Source Code**: Well-documented with JavaDoc comments
- **Tests**: Run `mvn test` to execute unit tests


