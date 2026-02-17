package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands;
import reactor.core.publisher.Mono;

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

    private final RedisScriptingReactiveCommands<String, String> commands;

    public FixedWindowRateLimiter(RedisScriptingReactiveCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitRule rule) {
        String redisKey = "rl:fw:" + key;
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
