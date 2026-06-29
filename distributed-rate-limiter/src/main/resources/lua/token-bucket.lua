-- Redis Lua script for atomic token bucket rate limiting
-- This script is designed for distributed rate limiters where multiple instances
-- need to share a single token bucket across a Redis cluster.
--
-- KEYS[1] - bucket key (e.g., "bucket:clientId:endpoint")
-- ARGV[1] - capacity (max tokens)
-- ARGV[2] - refill rate (tokens per second)
-- ARGV[3] - current timestamp in milliseconds
-- ARGV[4] - tokens to consume (typically 1)
-- ARGV[5] - TTL in seconds (time to live for the bucket key)
--
-- Returns: [success, tokensRemaining, refillTime, totalRequests, allowedRequests, rejectedRequests]
-- success: 1 if token consumed, 0 if rate limited
-- tokensRemaining: tokens left in bucket after this operation
-- refillTime: timestamp when bucket was last refilled
-- totalRequests: total requests attempted
-- allowedRequests: total requests allowed
-- rejectedRequests: total requests rejected

local bucketKey = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local nowMs = tonumber(ARGV[3])
local toConsume = tonumber(ARGV[4])
local ttl = tonumber(ARGV[5])

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
    -- Initialize new bucket
    tokens = capacity
    lastRefillMs = nowMs
    totalRequests = 0
    allowedRequests = 0
    rejectedRequests = 0
end

-- Calculate elapsed time and add tokens
local elapsedMs = math.max(0, nowMs - lastRefillMs)
local tokensToAdd = (elapsedMs / 1000.0) * refillRate
tokens = math.min(capacity, tokens + tokensToAdd)
lastRefillMs = nowMs

-- Attempt to consume
totalRequests = totalRequests + 1
local success = 0
if tokens >= toConsume then
    tokens = tokens - toConsume
    allowedRequests = allowedRequests + 1
    success = 1
else
    rejectedRequests = rejectedRequests + 1
    success = 0
end

-- Update bucket state in Redis
redis.call('HSET', bucketKey,
    'tokens', tostring(tokens),
    'lastRefillMs', tostring(lastRefillMs),
    'totalRequests', tostring(totalRequests),
    'allowedRequests', tostring(allowedRequests),
    'rejectedRequests', tostring(rejectedRequests)
)

-- Set TTL on the bucket key
if ttl > 0 then
    redis.call('EXPIRE', bucketKey, ttl)
end

-- Return result
return {
    success,
    tokens,
    lastRefillMs,
    totalRequests,
    allowedRequests,
    rejectedRequests
}

