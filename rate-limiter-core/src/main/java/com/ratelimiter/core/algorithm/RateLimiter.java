package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import reactor.core.publisher.Mono;

public interface RateLimiter {

    Mono<RateLimitResult> isAllowed(String key, RateLimitRule rule);
}
