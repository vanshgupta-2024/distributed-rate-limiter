package com.vansh.personal.ratelimiter.model;

/**
 * Simple rate limit configuration DTO.
 */
public class RateLimitConfig {

    private String id;
    private String pathPattern;
    private double requestsPerSecond;
    private double requestsPerMinute;
    private long ttlSeconds;
    private boolean requiresAuth;
    private String description;
    private boolean enabled = true;
    private int priority = 100;

    public RateLimitConfig() {
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }
    public double getRequestsPerSecond() { return requestsPerSecond; }
    public void setRequestsPerSecond(double requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }
    public double getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(double requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    public long getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    public boolean isRequiresAuth() { return requiresAuth; }
    public void setRequiresAuth(boolean requiresAuth) { this.requiresAuth = requiresAuth; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}

