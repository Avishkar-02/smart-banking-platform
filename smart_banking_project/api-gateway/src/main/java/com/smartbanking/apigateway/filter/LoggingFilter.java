package com.smartbanking.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// LoggingFilter runs on every request.
// Logs the incoming request details and the outgoing response status + duration.
// Order 2 = runs after CorrelationIdFilter (1) so correlation ID is already in MDC.

@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             GatewayFilterChain chain) {

        long startTime = System.currentTimeMillis();

        String method = exchange.getRequest().getMethod().name();
        String path   = exchange.getRequest().getURI().getPath();
        String corrId = exchange.getRequest()
                .getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);

        log.info("GATEWAY REQUEST  → {} {} [correlationId: {}]",
                method, path, corrId);

        // chain.filter() processes the request downstream.
        // .doOnSuccess() runs AFTER the response comes back.
        // .doOnError() runs if something throws during processing.
        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value()
                            : 0;
                    log.info("GATEWAY RESPONSE ← {} {} {} {}ms [correlationId: {}]",
                            status, method, path, duration, corrId);
                })
                .doOnError(err -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.error("GATEWAY ERROR    ← {} {} {}ms error: {} [correlationId: {}]",
                            method, path, duration, err.getMessage(), corrId);
                });
    }
}