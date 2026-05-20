package com.smartbanking.user_service.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    // RedisTemplate bean defined in RedisConfig.java — injected here via constructor.
    private final RedisTemplate<String, String> redisTemplate;

    public JwtService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private SecretKey getSigningKey() {
        // Convert raw string secret to cryptographic key object.
        // JWT requires SecretKey, not plain String.
        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    // Short-lived access token (1 hour).
    // Carries user identity so every request is self-contained — no DB lookup needed.
    public String generateAccessToken(String userUuid, String email, String role) {
        log.debug("Generating access token for UUID: {}", userUuid);

        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("role", role);
        // Distinguishes access tokens from refresh tokens — prevents misuse
        claims.put("type", "ACCESS");

        String token = Jwts.builder()
                .claims(claims)
                .subject(userUuid)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                // signWith creates a tamper-proof signature.
                // Any modification to the token breaks the signature.
                .signWith(getSigningKey())
                .compact();

        log.info("Access token generated for UUID: {}", userUuid);
        return token;
    }

    // Long-lived refresh token (7 days).
    // Used ONLY to get a new access token. Nothing else.
    public String generateRefreshToken(String userUuid) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "REFRESH");

        return Jwts.builder()
                .claims(claims)
                .subject(userUuid)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    // Parses AND verifies the token in one step.
    // Throws JwtException automatically if malformed, tampered, or expired.
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserUuid(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return (String) extractAllClaims(token).get("role");
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    // Three checks: parseable + not expired + not blacklisted
    public boolean isTokenValid(String token) {
        try {
            if (isTokenBlacklisted(token)) {
                log.warn("Rejected blacklisted token");
                return false;
            }
            if (isTokenExpired(token)) {
                log.warn("Rejected expired token");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    // Stores token in Redis with TTL = token's remaining lifetime.
    // Redis auto-deletes the key when TTL expires — no stale entries accumulate.
    public void blacklistToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            long remainingMs = claims.getExpiration().getTime()
                    - System.currentTimeMillis();

            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(
                        "blacklist:" + token,
                        "revoked",
                        remainingMs,
                        TimeUnit.MILLISECONDS);
                log.info("Token blacklisted in Redis with TTL: {}ms", remainingMs);
            } else {
                log.debug("Token already expired — skipping blacklist");
            }
        } catch (Exception e) {
            // Don't fail logout if Redis has issues — log and continue
            log.error("Failed to blacklist token: {}", e.getMessage());
        }
    }

    private boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
    }

    public Long getExpirationTime() {
        return jwtExpiration;
    }
}