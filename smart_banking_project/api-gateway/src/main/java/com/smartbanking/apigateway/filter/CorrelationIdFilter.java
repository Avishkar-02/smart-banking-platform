package com.smartbanking.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

// GlobalFilter runs on EVERY request through the gateway.
// This filter generates a correlation ID and adds it to both
// the request (forwarded to downstream services) and response
// (returned to client so they can reference it in support).
//
// Implements Ordered to control execution order.
// Order 1 = runs first, before LoggingFilter (Order 2) and AuthenticationFilter.
// This ensures correlation ID is set before any logging happens.

@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    // The header name used across ALL services for distributed tracing.
    // Defined here as a constant — same value used in common-lib CorrelationIdUtils.
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public int getOrder() {
        // Order 1 = highest priority after Ordered.HIGHEST_PRECEDENCE filters.
        // Runs before LoggingFilter (2) and everything else.
        return 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        // Try to read existing correlation ID from incoming request.
        // If client (or another gateway instance) already set it, reuse it.
        // If not present, generate a fresh UUID.
        String correlationId = exchange.getRequest()
                .getHeaders()
                .getFirst(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlationId: {}", correlationId);
        } else {
            log.debug("Reusing existing correlationId: {}", correlationId);
        }

        // Store in MDC so all log lines in this thread include it.
        // MDC = Mapped Diagnostic Context — thread-local key-value store for logging.
        final String finalCorrelationId = correlationId;
        MDC.put(CORRELATION_ID_HEADER, finalCorrelationId);

        // Mutate the request to add the correlation ID header.
        // This forwards the ID to downstream services (user-service, account-service etc.)
        // They can log it too, giving us a complete trace across services.
        ServerHttpRequest mutatedRequest = exchange.getRequest()
                .mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Mutate the response to add the correlation ID.
        // Client receives this in the response headers — useful for support tickets.
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        // Continue the filter chain with the mutated request.
        // Use .doFinally() to clear MDC after the request completes.
        // CRITICAL: without this, MDC leaks to next request on the same thread.
        return chain.filter(
                        exchange.mutate().request(mutatedRequest).build())
                .doFinally(signalType -> MDC.clear());
    }
}