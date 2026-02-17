package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface RateLimiter {

    RateLimitResult check(String key, RateLimitRule rule);

    default Mono<RateLimitResult> isAllowed(String key, RateLimitRule rule) {
        return Mono.fromCallable(() -> check(key, rule))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
