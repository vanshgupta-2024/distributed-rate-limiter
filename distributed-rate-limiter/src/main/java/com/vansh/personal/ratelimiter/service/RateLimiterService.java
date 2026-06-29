package com.vansh.personal.ratelimiter.service;

import com.vansh.personal.ratelimiter.model.RateLimitConfig;
import com.vansh.personal.ratelimiter.model.RateLimitStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory rate limiter service. Stores configs and token buckets in memory.
 * This provides a working implementation for local development and single-instance deployments.
 * For distributed deployments, use RedisRateLimiterService instead.
 *
 * Activated by default unless 'redis' profile is enabled.
 */
@Service
@Profile("!redis")
public class RateLimiterService implements IRateLimiterService {

    private final Map<String, RateLimitConfig> configs = new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // token buckets keyed by clientId + "|" + endpoint
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiterService() {
        // Add a default public rule
        RateLimitConfig c = new RateLimitConfig();
        c.setId("public-api");
        c.setPathPattern("/api/public/**");
        c.setRequestsPerSecond(10);
        c.setRequestsPerMinute(600);
        c.setTtlSeconds(3600);
        c.setRequiresAuth(false);
        c.setDescription("Default public API");
        c.setEnabled(true);
        c.setPriority(100);
        configs.put(c.getPathPattern(), c);
    }

    public RateLimitConfig registerConfig(RateLimitConfig cfg) {
        if (cfg.getPathPattern() == null) throw new IllegalArgumentException("pathPattern required");
        if (cfg.getRequestsPerSecond() <= 0) cfg.setRequestsPerSecond(1);
        configs.put(cfg.getPathPattern(), cfg);
        return cfg;
    }

    public Optional<RateLimitConfig> getConfig(String pathPattern) {
        return Optional.ofNullable(configs.get(pathPattern));
    }

    public List<RateLimitConfig> getAllConfigs() {
        List<RateLimitConfig> list = new ArrayList<>(configs.values());
        list.sort(Comparator.comparingInt(RateLimitConfig::getPriority).reversed());
        return list;
    }

    public Optional<RateLimitConfig> matchConfig(String path) {
        return configs.values().stream()
                .filter(RateLimitConfig::isEnabled)
                .filter(cfg -> pathMatcher.match(cfg.getPathPattern(), path))
                .max(Comparator.comparingInt(RateLimitConfig::getPriority));
    }

    public boolean tryConsume(String clientId, String endpoint, RateLimitConfig cfg) {
        String key = clientId + "|" + endpoint;
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(cfg.getRequestsPerSecond(), cfg.getRequestsPerSecond()));
        // If config changed, update bucket rates
        bucket.setRefillRate(cfg.getRequestsPerSecond());
        bucket.setCapacity(cfg.getRequestsPerSecond());
        // Synchronization is now at the bucket level for better concurrency
        return bucket.tryConsume();
    }

    public RateLimitStatus getStatus(String clientId, String endpoint) {
        String key = clientId + "|" + endpoint;
        TokenBucket bucket = buckets.get(key);
        RateLimitStatus s = new RateLimitStatus();
        s.setClientId(clientId);
        s.setEndpoint(endpoint);
        if (bucket == null) {
            s.setActive(false);
            s.setTokensAvailable(0);
            s.setTokensMax(0);
            s.setRefillRate(0);
            s.setLastRefillTime(0);
            s.setTotalRequests(0);
            s.setAllowedRequests(0);
            s.setRejectedRequests(0);
        } else {
            s.setActive(true);
            s.setTokensAvailable(bucket.getTokens());
            s.setTokensMax(bucket.getCapacity());
            s.setRefillRate(bucket.getRefillRate());
            s.setLastRefillTime(bucket.getLastRefillTime());
            s.setTotalRequests(bucket.getTotalRequests());
            s.setAllowedRequests(bucket.getAllowedRequests());
            s.setRejectedRequests(bucket.getRejectedRequests());
        }
        s.setTimestamp(Instant.now().toEpochMilli());
        return s;
    }

    public void reset(String clientId, String endpoint) {
        String key = clientId + "|" + endpoint;
        buckets.remove(key);
    }

    // simple token bucket implementation
    private static class TokenBucket {
        private double tokens;
        private double capacity;
        private double refillRate; // tokens per second
        private long lastRefillTime;
        private final AtomicLong totalRequests = new AtomicLong();
        private final AtomicLong allowedRequests = new AtomicLong();
        private final AtomicLong rejectedRequests = new AtomicLong();

        TokenBucket(double refillRate, double capacity) {
            this.refillRate = refillRate;
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            totalRequests.incrementAndGet();
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                allowedRequests.incrementAndGet();
                return true;
            } else {
                rejectedRequests.incrementAndGet();
                return false;
            }
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsedMillis = now - lastRefillTime;
            if (elapsedMillis <= 0) return;
            double toAdd = (elapsedMillis / 1000.0) * refillRate;
            if (toAdd > 0) {
                tokens = Math.min(capacity, tokens + toAdd);
                lastRefillTime = now;
            }
        }

        public double getTokens() { refill(); return tokens; }
        public double getCapacity() { return capacity; }
        public double getRefillRate() { return refillRate; }
        public long getLastRefillTime() { return lastRefillTime; }
        public long getTotalRequests() { return totalRequests.get(); }
        public long getAllowedRequests() { return allowedRequests.get(); }
        public long getRejectedRequests() { return rejectedRequests.get(); }
        public void setRefillRate(double r) { this.refillRate = r; }
        public void setCapacity(double c) { this.capacity = c; this.tokens = Math.min(tokens, capacity); }
    }
}

