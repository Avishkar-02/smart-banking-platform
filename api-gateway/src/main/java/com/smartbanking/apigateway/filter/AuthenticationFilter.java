package com.smartbanking.apigateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// AuthenticationFilter is a GatewayFilterFactory — it creates
// a GatewayFilter instance that can be applied to specific routes.
// Routes that declare "AuthenticationFilter" in their filters list
// get JWT validation. Public routes (login, register) do NOT declare it.
//
// This filter:
// 1. Checks for Authorization: Bearer <token> header
// 2. Validates the JWT (signature, expiry)
// 3. Extracts userUuid and role from token claims
// 4. Injects X-User-Uuid and X-User-Role headers into the forwarded request
// 5. Returns 401 if token is missing or invalid — downstream never sees bad requests

@Slf4j
@Component
public class AuthenticationFilter
        extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    // ObjectMapper for building error response JSON
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthenticationFilter() {
        super(Config.class);
    }

    // Config class — required by AbstractGatewayFilterFactory.
    // Empty because our filter needs no per-route configuration.
    // In future: could hold per-route settings like "skip expiry check" etc.
    public static class Config {
        // No configuration needed for now
    }

    @Override
    public GatewayFilter apply(Config config) {
        // Returns a GatewayFilter lambda that runs for each request on this route
        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            // Extract Authorization header
            String authHeader = request.getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            // No token present — reject immediately with 401
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Missing or malformed Authorization header for path: {}",
                        path);
                return buildErrorResponse(exchange,
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "Missing authorization token");
            }

            // Strip "Bearer " prefix to get raw JWT
            String token = authHeader.substring(7);

            try {
                // Parse and verify the JWT.
                // Jwts.parser().verifyWith() automatically checks:
                // 1. Signature matches our secret key
                // 2. Token is not malformed
                // 3. Token has not expired (exp claim < now)
                // If any check fails, it throws JwtException
                Claims claims = Jwts.parser()
                        .verifyWith(getSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // Extract identity from claims
                String userUuid = claims.getSubject();
                String role     = (String) claims.get("role");

                log.debug("JWT valid — userUuid: {}, role: {}, path: {}",
                        userUuid, role, path);

                // Inject user identity as trusted headers into the forwarded request.
                // Downstream services (account-service, transaction-service etc.)
                // read X-User-Uuid from the request header instead of parsing JWT themselves.
                // They trust this header because:
                // In production: firewall blocks direct access to services.
                //                Only Gateway can reach them.
                //                Gateway validated the JWT. Headers are trustworthy.
                // In development: you simulate this by setting headers manually in Postman.
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Uuid", userUuid)
                        .header("X-User-Role", role != null ? role : "CUSTOMER")
                        .build();

                return chain.filter(
                        exchange.mutate().request(mutatedRequest).build());

            } catch (JwtException e) {
                // Token is invalid — expired, malformed, wrong signature, etc.
                log.warn("JWT validation failed for path: {} — {}",
                        path, e.getMessage());
                return buildErrorResponse(exchange,
                        HttpStatus.UNAUTHORIZED,
                        "INVALID_TOKEN",
                        "Token is invalid or expired");
            }
        };
    }

    // Converts the raw secret string to a cryptographic SecretKey object.
    // Must match the exact same logic in user-service's JwtService.
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // Builds a JSON error response in our standard ApiResponse format.
    // Gateway returns this instead of letting the error propagate downstream.
    // Client always receives a clean JSON response, not a raw Spring error page.
    private Mono<Void> buildErrorResponse(ServerWebExchange exchange,
                                          HttpStatus status, String errorCode, String message) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Build the same ApiResponse structure our services use
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("status", "ERROR");
        errorBody.put("message", message);
        errorBody.put("errorCode", errorCode);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorBody);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            return response.setComplete();
        }
    }
}