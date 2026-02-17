package com.ratelimiter.core.algorithm;

import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands;
import reactor.core.publisher.Mono;

/**
 * Token Bucket algorithm.
 * Tokens are added at a fixed refill rate up to a max capacity.
 * Each request consumes one token. If no tokens are available, the request is denied.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private static final String SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local refill_rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])

            local data = redis.call('hmget', key, 'tokens', 'last_refill')
            local tokens = tonumber(data[1])
            local last_refill = tonumber(data[2])

            if tokens == nil then
                tokens = capacity
                last_refill = now
            end

            local elapsed = math.max(0, now - last_refill)
            tokens = math.min(capacity, tokens + elapsed * refill_rate)
            last_refill = now

            local allowed = 0
            local remaining = 0

            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
                remaining = math.floor(tokens)
            end

            redis.call('hmset', key, 'tokens', tokens, 'last_refill', last_refill)
            redis.call('expire', key, math.ceil(capacity / refill_rate) + 1)

            return {allowed, remaining}
            """;

    private final RedisScriptingReactiveCommands<String, String> commands;

    public TokenBucketRateLimiter(RedisScriptingReactiveCommands<String, String> commands) {
        this.commands = commands;
    }

    @Override
    public Mono<RateLimitResult> isAllowed(String key, RateLimitRule rule) {
        String redisKey = "rl:tb:" + key;
        long nowSeconds = System.currentTimeMillis() / 1000;

        String[] keys = {redisKey};
        String[] args = {
                String.valueOf(rule.getBucketCapacity()),
                String.valueOf(rule.getRefillRate()),
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
                        long retryAfter = (long) (1000.0 / rule.getRefillRate());
                        return RateLimitResult.denied(retryAfter);
                    }
                });
    }
}
