package com.smartbanking.apigateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

// RateLimiterConfig defines HOW we identify each user for rate limiting.
// The KeyResolver answers: "what is the key for THIS request?"
// Spring Cloud Gateway's RedisRateLimiter uses this key to track
// how many requests this user has made.

@Configuration
public class RateLimiterConfig {

    // userUuidKeyResolver extracts the user UUID from the X-User-Uuid header.
    // This header is injected by AuthenticationFilter after JWT validation.
    // So by the time rate limiting runs, X-User-Uuid is already set.
    //
    // Why UUID and not IP?
    // IP-based rate limiting fails when multiple users share a NAT IP (office networks).
    // UUID-based rate limiting is per-user — fair and precise.
    //
    // For public routes (login, register) where X-User-Uuid is not set,
    // we fall back to the remote IP address so public endpoints are also protected.
    @Bean
    public KeyResolver userUuidKeyResolver() {
        return exchange -> {
            // Try to get X-User-Uuid header first (set by AuthenticationFilter)
            String userUuid = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-User-Uuid");

            if (userUuid != null && !userUuid.isBlank()) {
                // Authenticated user — rate limit by their UUID
                return Mono.just(userUuid);
            }

            // Public route (no JWT) — rate limit by IP address
            // This prevents brute force attacks on login endpoint
            String remoteAddr = exchange.getRequest()
                    .getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress()
                    .getAddress().getHostAddress()
                    : "unknown";

            return Mono.just("anonymous:" + remoteAddr);
        };
    }
}