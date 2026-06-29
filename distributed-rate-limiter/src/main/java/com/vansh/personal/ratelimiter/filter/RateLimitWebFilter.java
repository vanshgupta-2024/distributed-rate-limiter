package com.vansh.personal.ratelimiter.filter;

import com.vansh.personal.ratelimiter.model.RateLimitConfig;
import com.vansh.personal.ratelimiter.service.IRateLimiterService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitWebFilter implements WebFilter {

    private final IRateLimiterService service;

    public RateLimitWebFilter(IRateLimiterService service) {
        this.service = service;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        // only apply to /api/**
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        RateLimitConfig cfg = service.matchConfig(path).orElse(null);
        if (cfg == null || !cfg.isEnabled()) {
            return chain.filter(exchange);
        }

        String clientId = extractClientId(exchange);
        // use endpoint as the path (could be normalized)
        String endpoint = path;

        boolean allowed = service.tryConsume(clientId, endpoint, cfg);

        // Set headers (simple values)
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf((int)cfg.getRequestsPerSecond()));
        // Remaining is approximate: we use status API to get tokens
        long remaining = (long) service.getStatus(clientId, endpoint).getTokensAvailable();
        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", String.valueOf(remaining));
        exchange.getResponse().getHeaders().add("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + 1000));

        if (allowed) {
            return chain.filter(exchange);
        } else {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            exchange.getResponse().getHeaders().add("Retry-After", "1");
            byte[] bytes = "{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded\", \"retry_after_seconds\": 1}".getBytes();
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        }
    }

    private String extractClientId(ServerWebExchange exchange) {
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");
        if (StringUtils.hasText(apiKey)) return "apiKey:" + apiKey;
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(auth)) return "auth:" + auth;
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) return "ip:" + xff.split(",")[0].trim();
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        if (addr != null) return "ip:" + addr.getAddress().getHostAddress();
        return "unknown";
    }
}

