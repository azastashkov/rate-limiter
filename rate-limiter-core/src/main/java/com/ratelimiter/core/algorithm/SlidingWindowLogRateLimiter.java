package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands;
import reactor.core.publisher.Mono;

/**
 * Sliding Window Log algorithm.
 * Keeps a sorted set of timestamps for each request.
 * Removes expired entries and checks if the count is within the limit.
 */
public class SlidingWindowLogRateLimiter implements RateLimiter {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window_size = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local request_id = ARGV[4]

            local window_start = now - window_size * 1000

            redis.call('zremrangebyscore', key, '-inf', window_start)

            local current = redis.call('zcard', key)

            local allowed = 0
            local remaining = 0

            if current < limit then
                redis.call('zadd', key, now, request_id)
                allowed = 1
                remaining = limit - current - 1
            end

            redis.call('expire', key, window_size + 1)

            return {allowed, remaining}
            """;

    private final RedisScriptingReactiveCommands<String, String> commands;

    public SlidingWindowLogRateLimiter(RedisScriptingReactiveCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitRule rule) {
        String redisKey = "rl:swl:" + key;
        long nowMillis = System.currentTimeMillis();
        String requestId = nowMillis + ":" + Thread.currentThread().threadId() + ":" + Math.random();

        String[] keys = {redisKey};
        String[] args = {
                String.valueOf(rule.getMaxRequests()),
                String.valueOf(rule.getWindowSizeSeconds()),
                String.valueOf(nowMillis),
                requestId
        };

        return commands.eval(SCRIPT, ScriptOutputType.MULTI, keys, args)
                .collectList()
                .map(result -> {
                    long allowed = (Long) result.get(0);
                    long remaining = (Long) result.get(1);
                    if (allowed == 1) {
                        return RateLimitResult.allowed(remaining);
                    } else {
                        return RateLimitResult.denied(rule.getWindowSizeSeconds() * 1000);
                    }
                });
    }
}
