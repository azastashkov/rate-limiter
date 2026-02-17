package com.ratelimiter.gateway.filter;

import com.ratelimiter.core.algorithm.RateLimiter;
import com.ratelimiter.core.algorithm.RateLimiterFactory;
import com.ratelimiter.core.config.RuleMatchService;
import com.ratelimiter.core.model.AlgorithmType;
import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitGlobalFilterTest {

    private RuleMatchService ruleMatchService;
    private RateLimiterFactory rateLimiterFactory;
    private RateLimitGlobalFilter filter;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        ruleMatchService = mock(RuleMatchService.class);
        rateLimiterFactory = mock(RateLimiterFactory.class);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter = new RateLimitGlobalFilter(ruleMatchService, rateLimiterFactory, new SimpleMeterRegistry());
    }

    @Test
    void passesRequestWhenNoRuleMatches() {
        when(ruleMatchService.findMatchingRule(anyString())).thenReturn(Optional.empty());

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        verifyNoInteractions(rateLimiterFactory);
    }

    @Test
    void allowsRequestWhenUnderLimit() {
        RateLimitRule rule = createRule(AlgorithmType.TOKEN_BUCKET);
        when(ruleMatchService.findMatchingRule("/api/resource")).thenReturn(Optional.of(rule));

        RateLimiter limiter = mock(RateLimiter.class);
        when(rateLimiterFactory.getLimiter(AlgorithmType.TOKEN_BUCKET)).thenReturn(limiter);
        when(limiter.isAllowed(anyString(), eq(rule))).thenReturn(Mono.just(RateLimitResult.allowed(9)));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/resource")
                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 1234))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
        assertEquals("9", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
    }

    @Test
    void deniesRequestWhenOverLimit() {
        RateLimitRule rule = createRule(AlgorithmType.TOKEN_BUCKET);
        when(ruleMatchService.findMatchingRule("/api/resource")).thenReturn(Optional.of(rule));

        RateLimiter limiter = mock(RateLimiter.class);
        when(rateLimiterFactory.getLimiter(AlgorithmType.TOKEN_BUCKET)).thenReturn(limiter);
        when(limiter.isAllowed(anyString(), eq(rule))).thenReturn(Mono.just(RateLimitResult.denied(500)));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/resource")
                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 1234))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals("0", exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"));
        verify(chain, never()).filter(exchange);
    }

    @Test
    void failsOpenOnError() {
        RateLimitRule rule = createRule(AlgorithmType.TOKEN_BUCKET);
        when(ruleMatchService.findMatchingRule("/api/resource")).thenReturn(Optional.of(rule));

        RateLimiter limiter = mock(RateLimiter.class);
        when(rateLimiterFactory.getLimiter(AlgorithmType.TOKEN_BUCKET)).thenReturn(limiter);
        when(limiter.isAllowed(anyString(), eq(rule))).thenReturn(Mono.error(new RuntimeException("Redis down")));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/resource")
                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 1234))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(chain).filter(exchange);
    }

    @Test
    void usesXForwardedForHeader() {
        RateLimitRule rule = createRule(AlgorithmType.TOKEN_BUCKET);
        when(ruleMatchService.findMatchingRule("/api/resource")).thenReturn(Optional.of(rule));

        RateLimiter limiter = mock(RateLimiter.class);
        when(rateLimiterFactory.getLimiter(AlgorithmType.TOKEN_BUCKET)).thenReturn(limiter);
        when(limiter.isAllowed(eq("10.0.0.1"), eq(rule))).thenReturn(Mono.just(RateLimitResult.allowed(5)));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/resource")
                .header("X-Forwarded-For", "10.0.0.1, 192.168.1.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, chain).block();

        verify(limiter).isAllowed(eq("10.0.0.1"), eq(rule));
    }

    private RateLimitRule createRule(AlgorithmType algorithm) {
        RateLimitRule rule = new RateLimitRule();
        rule.setId("test-rule");
        rule.setPath("/api/resource");
        rule.setAlgorithm(algorithm);
        rule.setMaxRequests(10);
        rule.setWindowSizeSeconds(60);
        rule.setBucketCapacity(10);
        rule.setRefillRate(2.0);
        return rule;
    }
}
