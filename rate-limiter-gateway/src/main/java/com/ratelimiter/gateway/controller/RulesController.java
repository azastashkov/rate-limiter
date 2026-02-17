package com.ratelimiter.gateway.controller;

import com.ratelimiter.core.config.RateLimitConfigService;
import com.ratelimiter.core.model.RateLimitRule;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/admin/rules")
public class RulesController {

    private final RateLimitConfigService configService;

    public RulesController(RateLimitConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public Mono<ResponseEntity<List<RateLimitRule>>> getRules() {
        return Mono.just(ResponseEntity.ok(configService.getRules()));
    }

    @PutMapping
    public Mono<ResponseEntity<String>> updateRules(@RequestBody List<RateLimitRule> rules) {
        return Mono.fromCallable(() -> {
            configService.updateRules(rules);
            return ResponseEntity.ok("Rules updated successfully");
        });
    }
}
