package com.ratelimiter.testapi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "test-api-service",
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/resource")
    public ResponseEntity<Map<String, Object>> getResource() {
        return ResponseEntity.ok(Map.of(
                "id", UUID.randomUUID().toString(),
                "data", "Sample resource data",
                "timestamp", Instant.now().toString()
        ));
    }

    @PostMapping("/resource")
    public ResponseEntity<Map<String, Object>> createResource(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "id", UUID.randomUUID().toString(),
                "data", body != null ? body : Map.of(),
                "created", true,
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/slow")
    public ResponseEntity<Map<String, Object>> slowEndpoint() throws InterruptedException {
        Thread.sleep(500);
        return ResponseEntity.ok(Map.of(
                "message", "Slow response completed",
                "delay_ms", 500,
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "name", "User " + userId,
                "email", "user" + userId + "@example.com",
                "timestamp", Instant.now().toString()
        ));
    }
}
