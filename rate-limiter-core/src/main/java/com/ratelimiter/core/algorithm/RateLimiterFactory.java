package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.AlgorithmType;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiterFactory {

    private final Map<AlgorithmType, RateLimiter> limiters = new ConcurrentHashMap<>();
    private final RedisCommands<String, String> commands;

    public RateLimiterFactory(RedisCommands<String, String> commands) {
        this.commands = commands;
    }

    public RateLimiter getLimiter(AlgorithmType type) {
        return limiters.computeIfAbsent(type, this::createLimiter);
    }

    private RateLimiter createLimiter(AlgorithmType type) {
        return switch (type) {
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(commands);
            case LEAKING_BUCKET -> new LeakingBucketRateLimiter(commands);
            case FIXED_WINDOW -> new FixedWindowRateLimiter(commands);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLogRateLimiter(commands);
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(commands);
        };
    }
}
