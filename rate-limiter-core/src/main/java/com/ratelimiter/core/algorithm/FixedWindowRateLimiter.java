package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;

/**
 * Fixed Window Counter algorithm.
 * Divides time into fixed windows and counts requests per window.
 * If the count exceeds the limit, the request is denied until the next window.
 */
public class FixedWindowRateLimiter implements RateLimiter {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window_size = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local window_start = now - (now % window_size)
            local window_key = key .. ':' .. window_start

            local current = tonumber(redis.call('get', window_key) or "0")

            local allowed = 0
            local remaining = 0

            if current < limit then
                redis.call('incr', window_key)
                redis.call('expire', window_key, window_size + 1)
                allowed = 1
                remaining = limit - current - 1
            else
                remaining = 0
            end

            local retry_after = (window_start + window_size - now) * 1000

            return {allowed, remaining, retry_after}
            """;

    private final RedisCommands<String, String> commands;

    public FixedWindowRateLimiter(RedisCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    @SuppressWarnings("unchecked")
    public RateLimitResult check(String key, RateLimitRule rule) {
        String redisKey = "rl:fw:" + key;
        long nowSeconds = System.currentTimeMillis() / 1000;

        String[] keys = {redisKey};
        String[] args = {
                String.valueOf(rule.getMaxRequests()),
                String.valueOf(rule.getWindowSizeSeconds()),
                String.valueOf(nowSeconds)
        };

        List<Long> result = commands.eval(SCRIPT, ScriptOutputType.MULTI, keys, args);
        long allowed = result.get(0);
        long remaining = result.get(1);
        long retryAfter = result.get(2);
        if (allowed == 1) {
            return RateLimitResult.allowed(remaining);
        } else {
            return RateLimitResult.denied(retryAfter);
        }
    }
}
