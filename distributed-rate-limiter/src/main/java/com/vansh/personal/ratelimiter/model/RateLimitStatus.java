package com.vansh.personal.ratelimiter.model;

/**
 * Status returned for a client+endpoint combination.
 */
public class RateLimitStatus {
    private String clientId;
    private String endpoint;
    private double tokensAvailable;
    private double tokensMax;
    private double refillRate;
    private long lastRefillTime;
    private long totalRequests;
    private long allowedRequests;
    private long rejectedRequests;
    private boolean isActive;
    private long timestamp;

    public RateLimitStatus() {}

    // Getters/Setters
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public double getTokensAvailable() { return tokensAvailable; }
    public void setTokensAvailable(double tokensAvailable) { this.tokensAvailable = tokensAvailable; }
    public double getTokensMax() { return tokensMax; }
    public void setTokensMax(double tokensMax) { this.tokensMax = tokensMax; }
    public double getRefillRate() { return refillRate; }
    public void setRefillRate(double refillRate) { this.refillRate = refillRate; }
    public long getLastRefillTime() { return lastRefillTime; }
    public void setLastRefillTime(long lastRefillTime) { this.lastRefillTime = lastRefillTime; }
    public long getTotalRequests() { return totalRequests; }
    public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
    public long getAllowedRequests() { return allowedRequests; }
    public void setAllowedRequests(long allowedRequests) { this.allowedRequests = allowedRequests; }
    public long getRejectedRequests() { return rejectedRequests; }
    public void setRejectedRequests(long rejectedRequests) { this.rejectedRequests = rejectedRequests; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

