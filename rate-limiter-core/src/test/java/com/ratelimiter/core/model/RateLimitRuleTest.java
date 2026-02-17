package com.ratelimiter.core.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitRuleTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializeAndDeserialize() throws Exception {
        RateLimitRule rule = new RateLimitRule();
        rule.setId("test-rule");
        rule.setPath("/api/test");
        rule.setAlgorithm(AlgorithmType.TOKEN_BUCKET);
        rule.setMaxRequests(100);
        rule.setWindowSizeSeconds(60);
        rule.setBucketCapacity(10);
        rule.setRefillRate(2.0);
        rule.setKeyResolver(KeyResolverType.IP);

        String json = objectMapper.writeValueAsString(rule);
        RateLimitRule deserialized = objectMapper.readValue(json, RateLimitRule.class);

        assertEquals("test-rule", deserialized.getId());
        assertEquals("/api/test", deserialized.getPath());
        assertEquals(AlgorithmType.TOKEN_BUCKET, deserialized.getAlgorithm());
        assertEquals(100, deserialized.getMaxRequests());
        assertEquals(60, deserialized.getWindowSizeSeconds());
        assertEquals(10, deserialized.getBucketCapacity());
        assertEquals(2.0, deserialized.getRefillRate());
        assertEquals(KeyResolverType.IP, deserialized.getKeyResolver());
    }

    @Test
    void deserializeFromJsonArray() throws Exception {
        String json = """
                [
                  {
                    "id": "rule-1",
                    "path": "/api/resource",
                    "algorithm": "token_bucket",
                    "bucketCapacity": 10,
                    "refillRate": 2.0,
                    "maxRequests": 10,
                    "windowSizeSeconds": 60,
                    "keyResolver": "ip"
                  },
                  {
                    "id": "rule-2",
                    "path": "/api/users/**",
                    "algorithm": "fixed_window",
                    "maxRequests": 5,
                    "windowSizeSeconds": 30,
                    "keyResolver": "ip_path"
                  }
                ]
                """;

        List<RateLimitRule> rules = objectMapper.readValue(json, new TypeReference<>() {});
        assertEquals(2, rules.size());
        assertEquals("rule-1", rules.get(0).getId());
        assertEquals(AlgorithmType.TOKEN_BUCKET, rules.get(0).getAlgorithm());
        assertEquals("rule-2", rules.get(1).getId());
        assertEquals(AlgorithmType.FIXED_WINDOW, rules.get(1).getAlgorithm());
        assertEquals(KeyResolverType.IP_PATH, rules.get(1).getKeyResolver());
    }

    @Test
    void defaultKeyResolverIsIp() {
        RateLimitRule rule = new RateLimitRule();
        assertEquals(KeyResolverType.IP, rule.getKeyResolver());
    }
}
