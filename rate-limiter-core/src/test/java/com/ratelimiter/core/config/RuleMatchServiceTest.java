package com.ratelimiter.core.config;

import com.ratelimiter.core.model.AlgorithmType;
import com.ratelimiter.core.model.RateLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RuleMatchServiceTest {

    private RuleMatchService ruleMatchService;

    @BeforeEach
    void setUp() {
        RateLimitRule exactRule = new RateLimitRule();
        exactRule.setId("exact");
        exactRule.setPath("/api/resource");
        exactRule.setAlgorithm(AlgorithmType.TOKEN_BUCKET);

        RateLimitRule wildcardRule = new RateLimitRule();
        wildcardRule.setId("wildcard");
        wildcardRule.setPath("/api/users/**");
        wildcardRule.setAlgorithm(AlgorithmType.FIXED_WINDOW);

        RateLimitRule catchAllRule = new RateLimitRule();
        catchAllRule.setId("catch-all");
        catchAllRule.setPath("/**");
        catchAllRule.setAlgorithm(AlgorithmType.SLIDING_WINDOW_COUNTER);

        StubConfigService configService = new StubConfigService(List.of(exactRule, wildcardRule, catchAllRule));
        ruleMatchService = new RuleMatchService(configService);
    }

    @Test
    void matchesExactPath() {
        Optional<RateLimitRule> result = ruleMatchService.findMatchingRule("/api/resource");
        assertTrue(result.isPresent());
        assertEquals("exact", result.get().getId());
    }

    @Test
    void matchesWildcardPath() {
        Optional<RateLimitRule> result = ruleMatchService.findMatchingRule("/api/users/123");
        assertTrue(result.isPresent());
        assertEquals("wildcard", result.get().getId());
    }

    @Test
    void matchesDeepWildcardPath() {
        Optional<RateLimitRule> result = ruleMatchService.findMatchingRule("/api/users/123/profile");
        assertTrue(result.isPresent());
        assertEquals("wildcard", result.get().getId());
    }

    @Test
    void fallsThroughToCatchAll() {
        Optional<RateLimitRule> result = ruleMatchService.findMatchingRule("/api/other");
        assertTrue(result.isPresent());
        assertEquals("catch-all", result.get().getId());
    }

    @Test
    void firstMatchWins() {
        Optional<RateLimitRule> result = ruleMatchService.findMatchingRule("/api/resource");
        assertTrue(result.isPresent());
        assertEquals("exact", result.get().getId());
    }

    private static class StubConfigService extends RateLimitConfigService {
        private final List<RateLimitRule> rules;

        StubConfigService(List<RateLimitRule> rules) {
            super(null, null);
            this.rules = rules;
        }

        @Override
        public List<RateLimitRule> getRules() {
            return rules;
        }
    }
}
