package com.vansh.personal.ratelimiter.controller;

import com.vansh.personal.ratelimiter.model.RateLimitConfig;
import com.vansh.personal.ratelimiter.model.RateLimitStatus;
import com.vansh.personal.ratelimiter.service.IRateLimiterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rate-limiter")
public class RateLimiterAdminController {

    private final IRateLimiterService service;

    public RateLimiterAdminController(IRateLimiterService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ResponseEntity<RateLimitStatus> status(@RequestParam String clientId, @RequestParam String endpoint) {
        return ResponseEntity.ok(service.getStatus(clientId, endpoint));
    }

    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestParam String clientId, @RequestParam String endpoint) {
        service.reset(clientId, endpoint);
        return ResponseEntity.ok().body("{\"message\": \"Rate limit reset successfully\", \"clientId\": \"" + clientId + "\", \"endpoint\": \"" + endpoint + "\"}");
    }

    @PostMapping("/config")
    public ResponseEntity<RateLimitConfig> registerConfig(@RequestBody RateLimitConfig cfg) {
        return ResponseEntity.ok(service.registerConfig(cfg));
    }

    @GetMapping("/config/{pathPattern}")
    public ResponseEntity<RateLimitConfig> getConfig(@PathVariable String pathPattern) {
        return service.getConfig(pathPattern)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/configs")
    public ResponseEntity<List<RateLimitConfig>> getAllConfigs() {
        return ResponseEntity.ok(service.getAllConfigs());
    }

    @GetMapping("/config/match")
    public ResponseEntity<RateLimitConfig> match(@RequestParam String path) {
        return service.matchConfig(path)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok().body("{\"status\": \"UP\", \"service\": \"rate-limiter\", \"timestamp\": \"" + System.currentTimeMillis() + "\"}");
    }
}

