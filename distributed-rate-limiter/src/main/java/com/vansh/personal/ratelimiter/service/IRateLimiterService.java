package com.vansh.personal.ratelimiter.service;

import com.vansh.personal.ratelimiter.model.RateLimitConfig;
import com.vansh.personal.ratelimiter.model.RateLimitStatus;

import java.util.List;
import java.util.Optional;

/**
 * Common interface for rate limiter service implementations (in-memory or Redis-backed).
 */
public interface IRateLimiterService {
    RateLimitConfig registerConfig(RateLimitConfig cfg);
    Optional<RateLimitConfig> getConfig(String pathPattern);
    List<RateLimitConfig> getAllConfigs();
    Optional<RateLimitConfig> matchConfig(String path);
    boolean tryConsume(String clientId, String endpoint, RateLimitConfig cfg);
    RateLimitStatus getStatus(String clientId, String endpoint);
    void reset(String clientId, String endpoint);
}

