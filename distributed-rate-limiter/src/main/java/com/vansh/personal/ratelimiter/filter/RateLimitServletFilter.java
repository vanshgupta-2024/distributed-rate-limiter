package com.vansh.personal.ratelimiter.filter;

import com.vansh.personal.ratelimiter.model.RateLimitConfig;
import com.vansh.personal.ratelimiter.service.IRateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;

@Component
public class RateLimitServletFilter extends OncePerRequestFilter {

    private final IRateLimiterService service;

    public RateLimitServletFilter(IRateLimiterService service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        RateLimitConfig cfg = service.matchConfig(path).orElse(null);
        if (cfg == null || !cfg.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = extractClientId(request);
        String endpoint = path;

        boolean allowed = service.tryConsume(clientId, endpoint, cfg);

        response.addHeader("X-RateLimit-Limit", String.valueOf((int)cfg.getRequestsPerSecond()));
        long remaining = (long) service.getStatus(clientId, endpoint).getTokensAvailable();
        response.addHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.addHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() + 1000));

        if (allowed) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            response.addHeader("Retry-After", "1");
            String body = "{\"error\": \"Too Many Requests\", \"message\": \"Rate limit exceeded\", \"retry_after_seconds\": 1}";
            response.getWriter().write(body);
        }
    }

    private String extractClientId(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (StringUtils.hasText(apiKey)) return "apiKey:" + apiKey;
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(auth)) return "auth:" + auth;
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) return "ip:" + xff.split(",")[0].trim();
        try {
            InetAddress addr = InetAddress.getByName(request.getRemoteAddr());
            if (addr != null) return "ip:" + addr.getHostAddress();
        } catch (Exception ignored) {}
        return "unknown";
    }
}

