-- Redis Lua script for fetching rate limit status
-- This script retrieves the current state of a rate limit bucket and calculates
-- the current token count including pending refill.
--
-- KEYS[1] - bucket key (e.g., "bucket:clientId:endpoint")
-- ARGV[1] - capacity (max tokens)
-- ARGV[2] - refill rate (tokens per second)
-- ARGV[3] - current timestamp in milliseconds
--
-- Returns: [tokensAvailable, capacity, lastRefillMs, totalRequests, allowedRequests, rejectedRequests]

local bucketKey = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local nowMs = tonumber(ARGV[3])

-- Fetch current bucket state
local bucket = redis.call('HGETALL', bucketKey)
local bucketExists = #bucket > 0

local tokens, lastRefillMs, totalRequests, allowedRequests, rejectedRequests

if bucketExists then
    -- Parse existing bucket state
    local bucketMap = {}
    for i = 1, #bucket, 2 do
        bucketMap[bucket[i]] = bucket[i + 1]
    end
    tokens = tonumber(bucketMap['tokens']) or capacity
    lastRefillMs = tonumber(bucketMap['lastRefillMs']) or nowMs
    totalRequests = tonumber(bucketMap['totalRequests']) or 0
    allowedRequests = tonumber(bucketMap['allowedRequests']) or 0
    rejectedRequests = tonumber(bucketMap['rejectedRequests']) or 0
else
    -- Bucket doesn't exist yet
    tokens = capacity
    lastRefillMs = nowMs
    totalRequests = 0
    allowedRequests = 0
    rejectedRequests = 0
end

-- Calculate tokens with pending refill (don't update Redis)
local elapsedMs = math.max(0, nowMs - lastRefillMs)
local tokensToAdd = (elapsedMs / 1000.0) * refillRate
tokens = math.min(capacity, tokens + tokensToAdd)

-- Return status
return {
    tokens,
    capacity,
    lastRefillMs,
    totalRequests,
    allowedRequests,
    rejectedRequests
}

