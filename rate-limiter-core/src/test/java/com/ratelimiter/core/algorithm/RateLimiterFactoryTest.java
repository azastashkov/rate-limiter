package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.AlgorithmType;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RateLimiterFactoryTest {

    @SuppressWarnings("unchecked")
    private final RedisCommands<String, String> commands =
            mock(RedisCommands.class);

    @Test
    void createsTokenBucketLimiter() {
        RateLimiterFactory factory = new RateLimiterFactory(commands);
        RateLimiter limiter = factory.getLimiter(AlgorithmType.TOKEN_BUCKET);
        assertInstanceOf(TokenBucketRateLimiter.class, limiter);
    }

    @Test
    void createsLeakingBucketLimiter() {
        RateLimiterFactory factory = new RateLimiterFactory(commands);
        RateLimiter limiter = factory.getLimiter(AlgorithmType.LEAKING_BUCKET);
        assertInstanceOf(LeakingBucketRateLimiter.class, limiter);
    }

    @Test
    void createsFixedWindowLimiter() {
        RateLimiterFactory factory = new RateLimiterFactory(commands);
        RateLimiter limiter = factory.getLimiter(AlgorithmType.FIXED_WINDOW);
        assertInstanceOf(FixedWindowRateLimiter.class, limiter);
    }

    @Test
    void createsSlidingWindowLogLimiter() {
        RateLimiterFactory factory = new RateLimiterFactory(commands);
        RateLimiter limiter = factory.getLimiter(AlgorithmType.SLIDING_WINDOW_LOG);
        assertInstanceOf(SlidingWindowLogRateLimiter.class, limiter);
    }

    @Test
    void createsSlidingWindowCounterLimiter() {
        RateLimiterFactory factory = new RateLimiterFactory(commands);
        RateLimiter limiter = factory.getLimiter(AlgorithmType.SLIDING_WINDOW_COUNTER);
        assertInstanceOf(SlidingWindowCounterRateLimiter.class, limiter);
    }

    @Test
    void returnsSameInstanceForSameType() {
        RateLimiterFactory factory = new RateLimiterFactory(commands);
        RateLimiter first = factory.getLimiter(AlgorithmType.TOKEN_BUCKET);
        RateLimiter second = factory.getLimiter(AlgorithmType.TOKEN_BUCKET);
        assertSame(first, second);
    }
}
