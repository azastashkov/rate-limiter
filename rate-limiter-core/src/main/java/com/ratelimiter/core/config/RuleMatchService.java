package com.ratelimiter.core.config;

import com.ratelimiter.core.model.RateLimitRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class RuleMatchService {

    private static final Logger log = LoggerFactory.getLogger(RuleMatchService.class);

    private final RateLimitConfigService configService;

    public RuleMatchService(RateLimitConfigService configService) {
        this.configService = configService;
    }

    public Optional<RateLimitRule> findMatchingRule(String path) {
        List<RateLimitRule> rules = configService.getRules();
        for (RateLimitRule rule : rules) {
            if (pathMatches(rule.getPath(), path)) {
                log.debug("Matched rule '{}' for path '{}'", rule.getId(), path);
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    private boolean pathMatches(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        if (pattern.equals("/**") || pattern.equals("*")) {
            return true;
        }
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return path.startsWith(prefix) && !path.substring(prefix.length() + 1).contains("/");
        }
        return pattern.equals(path);
    }
}
