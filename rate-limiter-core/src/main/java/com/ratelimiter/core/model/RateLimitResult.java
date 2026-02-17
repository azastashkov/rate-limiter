package com.ratelimiter.core.model;

public record RateLimitResult(
        boolean allowed,
        long remaining,
        long retryAfterMillis
) {
    public static RateLimitResult allowed(long remaining) {
        return new RateLimitResult(true, remaining, 0);
    }

    public static RateLimitResult denied(long retryAfterMillis) {
        return new RateLimitResult(false, 0, retryAfterMillis);
    }
}
