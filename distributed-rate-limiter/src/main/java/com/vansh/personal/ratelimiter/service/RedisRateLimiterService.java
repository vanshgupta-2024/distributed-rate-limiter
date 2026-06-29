package com.vansh.personal.ratelimiter.service;

import com.vansh.personal.ratelimiter.model.RateLimitConfig;
import com.vansh.personal.ratelimiter.model.RateLimitStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Redis-backed distributed rate limiter service.
 * Uses Lua scripts for atomic token bucket operations across multiple instances.
 * Each rate limiter bucket is stored in Redis and all consumption is atomic.
 */
@Slf4j
@Service
@Profile("redis")
public class RedisRateLimiterService implements IRateLimiterService {

    private final Map<String, RateLimitConfig> configs = new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<List> consumeTokenScript;
    private final RedisScript<List> statusScript;

    private static final String BUCKET_KEY_PREFIX = "rl:bucket:";
    private static final String CONFIG_KEY_PREFIX = "rl:config:";

    public RedisRateLimiterService(RedisTemplate<String, Object> redisTemplate) throws IOException {
        this.redisTemplate = redisTemplate;
        this.consumeTokenScript = RedisScript.of(loadScript("lua/token-bucket.lua"), List.class);
        this.statusScript = RedisScript.of(loadScript("lua/get-status.lua"), List.class);

        // Add default public rule
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

    /**
     * Load Lua script from classpath resources.
     */
    private String loadScript(String resourcePath) throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IOException("Lua script not found: " + resourcePath);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    public RateLimitConfig registerConfig(RateLimitConfig cfg) {
        if (cfg.getPathPattern() == null) throw new IllegalArgumentException("pathPattern required");
        if (cfg.getRequestsPerSecond() <= 0) cfg.setRequestsPerSecond(1);
        configs.put(cfg.getPathPattern(), cfg);
        log.info("Registered config: {} for pattern: {}", cfg.getId(), cfg.getPathPattern());
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

    /**
     * Atomically attempt to consume one token from the bucket.
     * Uses Redis Lua script to ensure consistency across instances.
     */
    public boolean tryConsume(String clientId, String endpoint, RateLimitConfig cfg) {
        String bucketKey = BUCKET_KEY_PREFIX + clientId + ":" + endpoint;
        long nowMs = System.currentTimeMillis();

        List<?> result = redisTemplate.execute(
                consumeTokenScript,
                Collections.singletonList(bucketKey),
                String.valueOf(cfg.getRequestsPerSecond()),  // capacity
                String.valueOf(cfg.getRequestsPerSecond()),  // refill rate tokens/sec
                String.valueOf(nowMs),                        // current time ms
                "1",                                          // tokens to consume
                String.valueOf(cfg.getTtlSeconds())           // TTL in seconds
        );

        if (result != null && !result.isEmpty()) {
            long success = ((Number) result.get(0)).longValue();
            return success == 1;
        }
        return false;
    }

    public RateLimitStatus getStatus(String clientId, String endpoint) {
        Optional<RateLimitConfig> cfgOpt = matchConfig(endpoint);

        RateLimitStatus s = new RateLimitStatus();
        s.setClientId(clientId);
        s.setEndpoint(endpoint);
        s.setTimestamp(Instant.now().toEpochMilli());

        if (cfgOpt.isEmpty()) {
            s.setActive(false);
            s.setTokensAvailable(0);
            s.setTokensMax(0);
            s.setRefillRate(0);
            s.setLastRefillTime(0);
            s.setTotalRequests(0);
            s.setAllowedRequests(0);
            s.setRejectedRequests(0);
            return s;
        }

        RateLimitConfig cfg = cfgOpt.get();
        String bucketKey = BUCKET_KEY_PREFIX + clientId + ":" + endpoint;
        long nowMs = System.currentTimeMillis();

        List<?> result = redisTemplate.execute(
                statusScript,
                Collections.singletonList(bucketKey),
                String.valueOf(cfg.getRequestsPerSecond()),  // capacity
                String.valueOf(cfg.getRequestsPerSecond()),  // refill rate
                String.valueOf(nowMs)                        // current time ms
        );

        s.setActive(true);
        if (result != null && result.size() >= 6) {
            s.setTokensAvailable(((Number) result.get(0)).doubleValue());
            s.setTokensMax(((Number) result.get(1)).doubleValue());
            s.setLastRefillTime(((Number) result.get(2)).longValue());
            s.setTotalRequests(((Number) result.get(3)).longValue());
            s.setAllowedRequests(((Number) result.get(4)).longValue());
            s.setRejectedRequests(((Number) result.get(5)).longValue());
        }
        return s;
    }

    public void reset(String clientId, String endpoint) {
        String bucketKey = BUCKET_KEY_PREFIX + clientId + ":" + endpoint;
        Boolean deleted = redisTemplate.delete(bucketKey);
        log.info("Reset bucket for clientId: {}, endpoint: {}, deleted: {}", clientId, endpoint, deleted);
    }
}



