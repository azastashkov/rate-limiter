package com.ratelimiter.gateway.config;

import com.ratelimiter.core.algorithm.RateLimiterFactory;
import io.lettuce.core.api.reactive.RedisScriptingReactiveCommands;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Bean
    public RateLimiterFactory rateLimiterFactory(RedisScriptingReactiveCommands<String, String> commands) {
        return new RateLimiterFactory(commands);
    }
}
