package com.ratelimiter.gateway.filter;

import com.ratelimiter.core.algorithm.RateLimiterFactory;
import com.ratelimiter.core.config.RuleMatchService;
import com.ratelimiter.core.model.KeyResolverType;
import com.ratelimiter.core.model.RateLimitResult;
import com.ratelimiter.core.model.RateLimitRule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGlobalFilter.class);

    private final RuleMatchService ruleMatchService;
    private final RateLimiterFactory rateLimiterFactory;
    private final Counter allowedCounter;
    private final Counter deniedCounter;

    public RateLimitGlobalFilter(RuleMatchService ruleMatchService,
                                 RateLimiterFactory rateLimiterFactory,
                                 MeterRegistry meterRegistry) {
        this.ruleMatchService = ruleMatchService;
        this.rateLimiterFactory = rateLimiterFactory;
        this.allowedCounter = Counter.builder("rate_limiter.requests.allowed")
                .description("Number of allowed requests")
                .register(meterRegistry);
        this.deniedCounter = Counter.builder("rate_limiter.requests.denied")
                .description("Number of denied requests")
                .register(meterRegistry);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        Optional<RateLimitRule> matchingRule = ruleMatchService.findMatchingRule(path);
        if (matchingRule.isEmpty()) {
            return chain.filter(exchange);
        }

        RateLimitRule rule = matchingRule.get();
        String key = resolveKey(exchange.getRequest(), rule);

        return rateLimiterFactory.getLimiter(rule.getAlgorithm())
                .isAllowed(key, rule)
                .flatMap(result -> handleResult(exchange, chain, result, rule))
                .onErrorResume(ex -> {
                    log.error("Rate limiter error, failing open for path: {}", path, ex);
                    return chain.filter(exchange);
                });
    }

    private Mono<Void> handleResult(ServerWebExchange exchange, GatewayFilterChain chain,
                                     RateLimitResult result, RateLimitRule rule) {
        if (result.allowed()) {
            allowedCounter.increment();
            ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(result.remaining()));
            response.getHeaders().add("X-RateLimit-Algorithm", rule.getAlgorithm().getValue());
            return chain.filter(exchange);
        } else {
            deniedCounter.increment();
            return writeRateLimitResponse(exchange, result);
        }
    }

    private Mono<Void> writeRateLimitResponse(ServerWebExchange exchange, RateLimitResult result) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Retry-After", String.valueOf(result.retryAfterMillis() / 1000));
        response.getHeaders().add("X-RateLimit-Remaining", "0");

        String body = "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please retry after "
                + result.retryAfterMillis() + "ms\",\"retryAfterMs\":" + result.retryAfterMillis() + "}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        response.getHeaders().add("Content-Type", "application/json");
        return response.writeWith(Mono.just(buffer));
    }

    private String resolveKey(ServerHttpRequest request, RateLimitRule rule) {
        KeyResolverType resolver = rule.getKeyResolver();
        String ip = extractIp(request);
        String path = request.getURI().getPath();

        return switch (resolver) {
            case IP -> ip;
            case PATH -> path;
            case USER -> {
                String user = request.getHeaders().getFirst("X-User-Id");
                yield user != null ? user : ip;
            }
            case IP_PATH -> ip + ":" + path;
        };
    }

    private String extractIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            InetAddress address = remoteAddress.getAddress();
            if (address != null) {
                return address.getHostAddress();
            }
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
