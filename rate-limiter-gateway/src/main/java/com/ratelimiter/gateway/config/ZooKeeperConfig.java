package com.ratelimiter.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.core.config.RateLimitConfigService;
import com.ratelimiter.core.config.RuleMatchService;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ZooKeeperConfig {

    @Value("${zookeeper.connect-string:localhost:2181}")
    private String connectString;

    @Value("${zookeeper.session-timeout-ms:5000}")
    private int sessionTimeoutMs;

    @Value("${zookeeper.connection-timeout-ms:3000}")
    private int connectionTimeoutMs;

    @Bean(initMethod = "start", destroyMethod = "close")
    public CuratorFramework curatorFramework() {
        return CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(sessionTimeoutMs)
                .connectionTimeoutMs(connectionTimeoutMs)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean(destroyMethod = "close")
    public RateLimitConfigService rateLimitConfigService(CuratorFramework curator, ObjectMapper objectMapper)
            throws Exception {
        RateLimitConfigService service = new RateLimitConfigService(curator, objectMapper);
        service.start();
        return service;
    }

    @Bean
    public RuleMatchService ruleMatchService(RateLimitConfigService configService) {
        return new RuleMatchService(configService);
    }
}
