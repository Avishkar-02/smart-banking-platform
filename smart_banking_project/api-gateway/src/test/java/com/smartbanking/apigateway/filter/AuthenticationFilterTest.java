package com.smartbanking.apigateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationFilter Unit Tests")
class AuthenticationFilterTest {

    private AuthenticationFilter authenticationFilter;

    private static final String SECRET =
            "testSecretKeyForJwtThatIsLongEnoughForHMACSHA256Algorithm";
    private static final String USER_UUID = "test-user-uuid-123";
    private static final String ROLE = "CUSTOMER";

    @BeforeEach
    void setUp() {
        authenticationFilter = new AuthenticationFilter();
        ReflectionTestUtils.setField(authenticationFilter, "jwtSecret", SECRET);
    }

    // Helper: builds a valid JWT token
    private String buildValidToken() {
        return Jwts.builder()
                .subject(USER_UUID)
                .claim("role", ROLE)
                .claim("type", "ACCESS")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(
                        SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // Helper: builds an expired JWT token
    private String buildExpiredToken() {
        return Jwts.builder()
                .subject(USER_UUID)
                .claim("role", ROLE)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000))
                .expiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(Keys.hmacShaKeyFor(
                        SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    @DisplayName("Valid JWT should pass through and inject X-User-Uuid header")
    void shouldPassValidJwtAndInjectHeaders() {
        String token = buildValidToken();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/accounts/create")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        // Capture the mutated exchange to check injected headers
        when(chain.filter(any())).thenAnswer(invocation -> {
            var ex = (org.springframework.web.server.ServerWebExchange)
                    invocation.getArgument(0);
            // Verify X-User-Uuid was injected
            String injectedUuid = ex.getRequest().getHeaders()
                    .getFirst("X-User-Uuid");
            assertThat(injectedUuid).isEqualTo(USER_UUID);

            String injectedRole = ex.getRequest().getHeaders()
                    .getFirst("X-User-Role");
            assertThat(injectedRole).isEqualTo(ROLE);

            return Mono.empty();
        });

        GatewayFilter filter = authenticationFilter.apply(
                new AuthenticationFilter.Config());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    @DisplayName("Missing Authorization header should return 401")
    void shouldReturn401WhenNoHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/accounts/create")
                // No Authorization header
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        GatewayFilter filter = authenticationFilter.apply(
                new AuthenticationFilter.Config());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Response should be 401
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Expired JWT should return 401")
    void shouldReturn401ForExpiredToken() {
        String expiredToken = buildExpiredToken();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/accounts/create")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        GatewayFilter filter = authenticationFilter.apply(
                new AuthenticationFilter.Config());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Garbage token string should return 401")
    void shouldReturn401ForGarbageToken() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/accounts/create")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.jwt")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        GatewayFilter filter = authenticationFilter.apply(
                new AuthenticationFilter.Config());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Wrong 'Bearer' prefix format should return 401")
    void shouldReturn401ForWrongPrefix() {
        String token = buildValidToken();

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/accounts/create")
                // Using 'Token' prefix instead of 'Bearer'
                .header(HttpHeaders.AUTHORIZATION, "Token " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        GatewayFilter filter = authenticationFilter.apply(
                new AuthenticationFilter.Config());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}