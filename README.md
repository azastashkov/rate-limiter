# Rate Limiter

Distributed rate limiting library and API gateway built with Java 21, Spring Cloud Gateway, Redis, and ZooKeeper. Implements five rate limiting algorithms with dynamic rule configuration, horizontal scaling, and observability.

## Architecture

```
                    ┌──────────────┐
                    │  Test Client │
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
     ┌──────────────────┐      ┌──────────────────┐
     │ Rate Limiter GW  │      │ Rate Limiter GW  │
     │    (port 8081)   │      │    (port 8082)   │
     └────┬──────┬──────┘      └──────┬──────┬────┘
          │      │                    │      │
          │      └────────┬───────────┘      │
          │               ▼                  │
          │        ┌─────────────┐           │
          │        │    Redis    │           │
          │        │  (state)    │           │
          │        └─────────────┘           │
          │               ▼                  │
          │        ┌─────────────┐           │
          └───────►│  ZooKeeper  │◄──────────┘
                   │  (config)   │
                   └─────────────┘
                          │
                          ▼
                   ┌─────────────┐
                   │  Test API   │
                   │  Service    │
                   └─────────────┘
```

Multiple gateway instances share rate limit state via Redis and configuration via ZooKeeper. Rules can be updated at runtime without restarts.

## Algorithms

| Algorithm | Description | Use Case |
|---|---|---|
| **Token Bucket** | Tokens refill at a fixed rate up to a maximum capacity. Each request consumes one token. | Allows short bursts while enforcing an average rate. |
| **Leaking Bucket** | Requests fill a bucket that drains at a constant rate. Rejects when full. | Smooths traffic into a steady output rate. |
| **Fixed Window** | Counts requests in fixed time intervals (e.g., per minute). Resets at window boundaries. | Simple and predictable. Susceptible to boundary spikes. |
| **Sliding Window Log** | Maintains a timestamp log of each request within the window. Removes expired entries. | Most accurate. Higher memory usage per client. |
| **Sliding Window Counter** | Approximates a sliding window using weighted counts from the current and previous fixed windows. | Good accuracy with low memory overhead. |

All algorithms execute atomically via Redis Lua scripts and return a result containing: whether the request is allowed, remaining quota, and retry-after delay.

## Project Structure

```
rate-limiter/
├── rate-limiter-core/          Core library (algorithms, models, config)
├── rate-limiter-gateway/       Spring Cloud Gateway with rate limiting filter
├── test-api-service/           Mock upstream API for testing
├── test-client/                Load testing client
├── config/
│   ├── init-rules.json         Default rate limit rules
│   ├── prometheus.yml          Prometheus scrape config
│   └── grafana/                Grafana dashboard provisioning
└── docker-compose.yml          Full stack orchestration
```

### Modules

**rate-limiter-core** — Algorithm implementations (`TokenBucketRateLimiter`, `LeakingBucketRateLimiter`, `FixedWindowRateLimiter`, `SlidingWindowLogRateLimiter`, `SlidingWindowCounterRateLimiter`), configuration management via ZooKeeper (`RateLimitConfigService`), path-based rule matching with wildcard support (`RuleMatchService`), and shared models (`RateLimitRule`, `RateLimitResult`).

**rate-limiter-gateway** — Spring Cloud Gateway application with a global filter (`RateLimitGlobalFilter`) that intercepts requests, matches rules by path, resolves client keys, and enforces rate limits. Returns `429 Too Many Requests` with `Retry-After` header when limits are exceeded. Includes an admin REST API for rule management.

**test-api-service** — Simple Spring Boot web service with endpoints (`/api/resource`, `/api/users/{id}`, `/api/slow`, `/api/health`) used as the upstream target.

**test-client** — Configurable load testing tool that sends concurrent requests and reports allowed/denied/error counts.

## Key Resolution

Each rule specifies how to identify the client being rate limited:

| Key Resolver | Strategy |
|---|---|
| `ip` | Client IP address (supports `X-Forwarded-For`) |
| `user` | `X-User-Id` header |
| `path` | Request path |
| `ip_path` | Combination of IP and path |

## Response Headers

On allowed requests:
- `X-RateLimit-Remaining` — remaining quota
- `X-RateLimit-Algorithm` — algorithm used

On denied requests (HTTP 429):
- `Retry-After` — seconds until the client should retry
- JSON body with error details

## Prerequisites

- Java 21
- Docker and Docker Compose

## Quick Start

### Run the full stack with Docker Compose

```bash
docker compose up --build
```

This starts Redis, ZooKeeper, two gateway instances, the test API service, Prometheus, and Grafana.

| Service | URL |
|---|---|
| Gateway 1 | http://localhost:8081 |
| Gateway 2 | http://localhost:8082 |
| Test API | http://localhost:9090 |
| Prometheus | http://localhost:9091 |
| Grafana | http://localhost:3000 (admin/admin) |
| ZooNavigator | http://localhost:9000 |

### Send test traffic

```bash
# Single request through the gateway
curl http://localhost:8081/api/resource

# Run the load test client
docker compose --profile test up test-client
```

### Build locally

```bash
./gradlew build
```

### Run tests

```bash
./gradlew test
```

## Rule Configuration

Rules are stored in ZooKeeper at `/rate-limiter/rules` and loaded dynamically. Changes are picked up without restarts.

### Default rules (config/init-rules.json)

| Rule ID | Path | Algorithm | Limits |
|---|---|---|---|
| `api-resource-token-bucket` | `/api/resource` | Token Bucket | capacity=10, refill=2.0/s |
| `api-users-fixed-window` | `/api/users/**` | Fixed Window | 5 req / 60s |
| `api-slow-leaking-bucket` | `/api/slow` | Leaking Bucket | capacity=3, leak=0.5/s |
| `api-health-sliding-window-log` | `/api/health` | Sliding Window Log | 20 req / 60s |
| `default-sliding-window-counter` | `/**` | Sliding Window Counter | 100 req / 60s |

### Update rules at runtime

```bash
curl -X PUT http://localhost:8081/admin/rules \
  -H "Content-Type: application/json" \
  -d '[
    {
      "id": "custom-rule",
      "path": "/api/resource",
      "algorithm": "token_bucket",
      "bucketCapacity": 20,
      "refillRate": 5.0,
      "keyResolver": "ip"
    }
  ]'
```

### Path matching

- Exact: `/api/resource`
- Single-level wildcard: `/api/users/*`
- Multi-level wildcard: `/api/users/**`
- Catch-all: `/**`

The first matching rule wins.

## Observability

Prometheus metrics are exported via Spring Boot Actuator:

- `rate_limiter_requests_allowed` — counter of allowed requests
- `rate_limiter_requests_denied` — counter of denied requests

Grafana is pre-configured with a Prometheus data source. Access dashboards at http://localhost:3000.

## Design Decisions

- **Fail-open** — if Redis is unavailable, requests are allowed through rather than blocked
- **Atomic Lua scripts** — all rate limit checks are single Redis round-trips, avoiding race conditions
- **Horizontal scaling** — multiple gateway instances share state via Redis; no sticky sessions required
- **Dynamic configuration** — ZooKeeper watches push rule changes to all instances in real time

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Build | Gradle (Groovy DSL) |
| Gateway | Spring Cloud Gateway 2023.0.4 |
| Web Framework | Spring Boot 3.3.6 (WebFlux) |
| Redis Client | Lettuce 6.4.0 |
| Configuration Store | Apache ZooKeeper 3.9 (Curator 5.7.1) |
| Metrics | Micrometer + Prometheus |
| Dashboards | Grafana 11.1.0 |
| Testing | JUnit 5, Mockito |
