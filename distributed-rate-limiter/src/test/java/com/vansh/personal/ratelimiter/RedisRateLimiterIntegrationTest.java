package com.vansh.personal.ratelimiter;

import com.vansh.personal.ratelimiter.model.RateLimitConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Redis-backed rate limiter.
 * Skips if Testcontainers/Docker not available.
 */
public class RedisRateLimiterIntegrationTest {

    private static String redisHost;
    private static int redisPort;
    private static com.vansh.personal.ratelimiter.service.RedisRateLimiterService service;

    @BeforeAll
    public static void startRedisAndInit() throws Exception {
        // For this test to run, a Redis instance must be available.
        // This is typically provided by docker-compose or a local Redis server.
        // If neither is available, skip this test.
        String redisHostEnv = System.getenv("REDIS_HOST");
        String redisPortEnv = System.getenv("REDIS_PORT");

        redisHost = redisHostEnv != null ? redisHostEnv : "localhost";
        redisPort = redisPortEnv != null ? Integer.parseInt(redisPortEnv) : 6379;

        // Try to establish connection; if it fails, skip the test
        try {
            LettuceConnectionFactory testFactory = new LettuceConnectionFactory(redisHost, redisPort);
            testFactory.setTimeout(2000); // 2 seconds in milliseconds
            testFactory.afterPropertiesSet();

            // Quick connectivity check
            testFactory.getConnection().ping();
            testFactory.destroy();
        } catch (Exception e) {
            Assumptions.assumeTrue(false,
                    "Redis not available at " + redisHost + ":" + redisPort + ". " +
                    "Start Redis or use: docker compose up. Skipping test. Error: " + e.getMessage());
        }

        // Create LettuceConnectionFactory and RedisTemplate manually
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(redisHost, redisPort);
        connectionFactory.afterPropertiesSet();
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> jsonSerializer = new Jackson2JsonRedisSerializer<>(Object.class);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        service = new com.vansh.personal.ratelimiter.service.RedisRateLimiterService(template);
    }

    @AfterAll
    public static void stopRedis() {
        // No cleanup needed; external Redis instance manages itself
    }

    @Test
    public void testRedisTokenBucketConsume() throws Exception {
        RateLimitConfig cfg = new RateLimitConfig();
        cfg.setId("test-api");
        cfg.setPathPattern("/api/test/**");
        cfg.setRequestsPerSecond(2);
        cfg.setTtlSeconds(60);
        cfg.setEnabled(true);

        service.registerConfig(cfg);

        String clientId = "test-client";
        String endpoint = "/api/test/1";

        // First two requests should be allowed (capacity == 2)
        boolean a1 = service.tryConsume(clientId, endpoint, cfg);
        boolean a2 = service.tryConsume(clientId, endpoint, cfg);
        boolean a3 = service.tryConsume(clientId, endpoint, cfg);

        assertThat(a1).isTrue();
        assertThat(a2).isTrue();
        assertThat(a3).isFalse();

        // Wait ~600ms for refill (2 tokens/sec => ~1.2 tokens in 600ms) then should allow one
        Thread.sleep(600);
        boolean a4 = service.tryConsume(clientId, endpoint, cfg);
        assertThat(a4).isTrue();
    }
}










