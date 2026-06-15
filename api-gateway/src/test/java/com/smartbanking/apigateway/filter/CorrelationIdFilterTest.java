package com.smartbanking.apigateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("CorrelationIdFilter Unit Tests")
class CorrelationIdFilterTest {

    @InjectMocks
    private CorrelationIdFilter correlationIdFilter;

    @Test
    @DisplayName("Should generate correlation ID when not present in request")
    void shouldGenerateCorrelationId() {
        // Build a request WITHOUT X-Correlation-ID header
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(correlationIdFilter.filter(exchange, chain))
                .verifyComplete();

        // Response should have X-Correlation-ID header set
        String corrId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(corrId).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("Should reuse existing correlation ID from request")
    void shouldReuseExistingCorrelationId() {
        String existingId = "existing-correlation-id-123";

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, existingId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(correlationIdFilter.filter(exchange, chain))
                .verifyComplete();

        // Response should have the SAME correlation ID — not a new one
        String responseCorrelationId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(responseCorrelationId).isEqualTo(existingId);
    }

    @Test
    @DisplayName("Filter order should be 1 (runs first)")
    void shouldHaveOrderOne() {
        assertThat(correlationIdFilter.getOrder()).isEqualTo(1);
    }
}