package com.ratelimiter.testclient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitTestRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RateLimitTestRunner.class);

    @Value("${gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    @Value("${test.requests:20}")
    private int totalRequests;

    @Value("${test.concurrent:5}")
    private int concurrent;

    @Value("${test.endpoint:/api/resource}")
    private String endpoint;

    @Value("${test.delay-ms:100}")
    private long delayMs;

    @Override
    public void run(String... args) {
        WebClient client = WebClient.builder()
                .baseUrl(gatewayUrl)
                .build();

        log.info("=== Rate Limiter Test Client ===");
        log.info("Gateway: {}", gatewayUrl);
        log.info("Endpoint: {}", endpoint);
        log.info("Total requests: {}, Concurrency: {}, Delay: {}ms", totalRequests, concurrent, delayMs);
        log.info("================================");

        AtomicInteger allowed = new AtomicInteger(0);
        AtomicInteger denied = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        Flux.range(1, totalRequests)
                .flatMap(i -> sendRequest(client, i, allowed, denied, errors), concurrent)
                .delayElements(Duration.ofMillis(delayMs))
                .blockLast(Duration.ofMinutes(5));

        long elapsed = System.currentTimeMillis() - startTime;

        log.info("================================");
        log.info("=== Test Results ===");
        log.info("Total requests: {}", totalRequests);
        log.info("Allowed (2xx): {}", allowed.get());
        log.info("Denied (429):  {}", denied.get());
        log.info("Errors:        {}", errors.get());
        log.info("Time elapsed:  {}ms", elapsed);
        log.info("====================");
    }

    private Mono<Void> sendRequest(WebClient client, int requestNum,
                                    AtomicInteger allowed, AtomicInteger denied, AtomicInteger errors) {
        return client.get()
                .uri(endpoint)
                .exchangeToMono(response -> {
                    HttpStatus status = (HttpStatus) response.statusCode();
                    String remaining = response.headers().asHttpHeaders().getFirst("X-RateLimit-Remaining");
                    String algorithm = response.headers().asHttpHeaders().getFirst("X-RateLimit-Algorithm");

                    if (status == HttpStatus.TOO_MANY_REQUESTS) {
                        denied.incrementAndGet();
                        String retryAfter = response.headers().asHttpHeaders().getFirst("Retry-After");
                        log.warn("[{}] DENIED - 429 Too Many Requests (Retry-After: {}s)", requestNum, retryAfter);
                    } else if (status.is2xxSuccessful()) {
                        allowed.incrementAndGet();
                        log.info("[{}] ALLOWED - {} (Remaining: {}, Algorithm: {})",
                                requestNum, status.value(), remaining, algorithm);
                    } else {
                        errors.incrementAndGet();
                        log.error("[{}] ERROR - {}", requestNum, status.value());
                    }
                    return response.releaseBody();
                })
                .onErrorResume(ex -> {
                    errors.incrementAndGet();
                    log.error("[{}] CONNECTION ERROR - {}", requestNum, ex.getMessage());
                    return Mono.empty();
                });
    }
}
