package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands;
import reactor.core.publisher.Mono;

/**
 * Sliding Window Counter algorithm.
 * Combines fixed window counter with a weighted count from the previous window
 * to approximate a sliding window. Uses less memory than the sliding window log.
 */
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window_size = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local current_window = now - (now % window_size)
            local previous_window = current_window - window_size

            local current_key = key .. ':' .. current_window
            local previous_key = key .. ':' .. previous_window

            local current_count = tonumber(redis.call('get', current_key) or "0")
            local previous_count = tonumber(redis.call('get', previous_key) or "0")

            local elapsed_in_current = now - current_window
            local weight = 1 - (elapsed_in_current / window_size)
            local weighted_count = math.floor(previous_count * weight + current_count)

            local allowed = 0
            local remaining = 0

            if weighted_count < limit then
                redis.call('incr', current_key)
                redis.call('expire', current_key, window_size * 2 + 1)
                allowed = 1
                remaining = limit - weighted_count - 1
            end

            local retry_after = (current_window + window_size - now) * 1000

            return {allowed, remaining, retry_after}
            """;

    private final RedisScriptingReactiveCommands<String, String> commands;

    public SlidingWindowCounterRateLimiter(RedisScriptingReactiveCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitRule rule) {
        String redisKey = "rl:swc:" + key;
        long nowSeconds = System.currentTimeMillis() / 1000;

        String[] keys = {redisKey};
        String[] args = {
                String.valueOf(rule.getMaxRequests()),
                String.valueOf(rule.getWindowSizeSeconds()),
                String.valueOf(nowSeconds)
        };

        return commands.eval(SCRIPT, ScriptOutputType.MULTI, keys, args)
                .collectList()
                .map(result -> {
                    long allowed = (Long) result.get(0);
                    long remaining = (Long) result.get(1);
                    long retryAfter = (Long) result.get(2);
                    if (allowed == 1) {
                        return RateLimitResult.allowed(remaining);
                    } else {
                        return RateLimitResult.denied(retryAfter);
                    }
                });
    }
}
