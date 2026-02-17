package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands;
import reactor.core.publisher.Mono;

/**
 * Leaking Bucket algorithm.
 * Requests are added to a queue (bucket). The bucket leaks at a fixed rate.
 * If the bucket is full, the request is denied.
 */
public class LeakingBucketRateLimiter implements RateLimiter {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local leak_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local data = redis.call('hmget', key, 'water', 'last_leak')
            local water = tonumber(data[1])
            local last_leak = tonumber(data[2])

            if water == nil then
                water = 0
                last_leak = now
            end

            local elapsed = math.max(0, now - last_leak)
            local leaked = elapsed * leak_rate
            water = math.max(0, water - leaked)
            last_leak = now

            local allowed = 0
            local remaining = 0

            if water < capacity then
                water = water + 1
                allowed = 1
                remaining = math.floor(capacity - water)
            end

            redis.call('hmset', key, 'water', water, 'last_leak', last_leak)
            redis.call('expire', key, math.ceil(capacity / leak_rate) + 1)

            return {allowed, remaining}
            """;

    private final RedisScriptingReactiveCommands<String, String> commands;

    public LeakingBucketRateLimiter(RedisScriptingReactiveCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitRule rule) {
        String redisKey = "rl:lb:" + key;
        long nowSeconds = System.currentTimeMillis() / 1000;

        String[] keys = {redisKey};
        String[] args = {
                String.valueOf(rule.getBucketCapacity()),
                String.valueOf(rule.getLeakRate()),
                String.valueOf(nowSeconds)
        };

        return commands.eval(SCRIPT, ScriptOutputType.MULTI, keys, args)
                .collectList()
                .map(result -> {
                    long allowed = (Long) result.get(0);
                    long remaining = (Long) result.get(1);
                    if (allowed == 1) {
                        return RateLimitResult.allowed(remaining);
                    } else {
                        long retryAfter = (long) (1000.0 / rule.getLeakRate());
                        return RateLimitResult.denied(retryAfter);
                    }
                });
    }
}
