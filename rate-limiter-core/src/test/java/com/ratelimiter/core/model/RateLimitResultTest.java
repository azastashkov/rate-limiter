package com.ratelimiter.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitResultTest {

    @Test
    void allowedResult() {
        RateLimitResult result = RateLimitResult.allowed(5);
        assertTrue(result.allowed());
        assertEquals(5, result.remaining());
        assertEquals(0, result.retryAfterMillis());
    }

    @Test
    void deniedResult() {
        RateLimitResult result = RateLimitResult.denied(1000);
        assertFalse(result.allowed());
        assertEquals(0, result.remaining());
        assertEquals(1000, result.retryAfterMillis());
    }
}
