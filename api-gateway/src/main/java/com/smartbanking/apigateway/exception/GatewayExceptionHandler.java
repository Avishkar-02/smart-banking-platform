package com.smartbanking.apigateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

// GatewayExceptionHandler is the global error handler for the gateway itself.
// It catches exceptions that are NOT handled by individual filters.
// Key scenarios:
// - NotFoundException: target service has no instances in Eureka → 503
// - ResponseStatusException: Spring throws this for various HTTP errors
// - Any unexpected RuntimeException → 500
//
// @Order(-1) makes this run BEFORE Spring Boot's default error handler.
// Without this, errors would return the default Whitelabel Error Page.

@Slf4j
@Component
@Order(-1)
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

        HttpStatus status;
        String errorCode;
        String message;

        if (ex instanceof NotFoundException) {
            // No instances of target service available in Eureka.
            // This happens when the downstream service is down or not registered.
            status    = HttpStatus.SERVICE_UNAVAILABLE;
            errorCode = "SERVICE_UNAVAILABLE";
            message   = "Service temporarily unavailable. Please try again.";
            log.error("No service instances available: {}", ex.getMessage());

        } else if (ex instanceof ResponseStatusException rse) {
            // Spring's standard HTTP exception — uses its status and reason
            status    = HttpStatus.valueOf(rse.getStatusCode().value());
            errorCode = "GATEWAY_ERROR";
            message   = rse.getReason() != null
                    ? rse.getReason() : "Request could not be processed";
            log.error("Response status exception: {} {}",
                    rse.getStatusCode(), rse.getReason());

        } else {
            // Catch-all — never expose internal stack traces to clients
            status    = HttpStatus.INTERNAL_SERVER_ERROR;
            errorCode = "INTERNAL_SERVER_ERROR";
            message   = "An unexpected error occurred";
            log.error("Unexpected gateway error: ", ex);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders()
                .setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("status", "ERROR");
        body.put("message", message);
        body.put("errorCode", errorCode);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse()
                    .bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to write error response", e);
            return exchange.getResponse().setComplete();
        }
    }
}