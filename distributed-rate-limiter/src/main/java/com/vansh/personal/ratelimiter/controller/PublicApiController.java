package com.vansh.personal.ratelimiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicApiController {

    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok().body("{\"message\": \"public test OK\"}");
    }
}

