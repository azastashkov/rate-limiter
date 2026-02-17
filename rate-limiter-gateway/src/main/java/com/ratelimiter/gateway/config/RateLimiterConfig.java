package com.ratelimiter.gateway.config;

import com.ratelimiter.core.algorithm.RateLimiterFactory;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Bean
    public RateLimiterFactory rateLimiterFactory(RedisCommands<String, String> commands) {
        return new RateLimiterFactory(commands);
    }
}
