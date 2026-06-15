package com.smartbanking.user_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Pure unit test — no Spring context, no real Redis.
// Tests JwtService logic in complete isolation.
// Runs in milliseconds.

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Tests")
class JwtServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtService jwtService;

    private static final String TEST_UUID = "test-uuid-123";
    private static final String TEST_EMAIL = "test@test.com";
    private static final String TEST_ROLE = "CUSTOMER";
    private static final String TEST_SECRET =
            "testSecretKeyForJwtThatIsLongEnoughForHMACSHA256Algorithm";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(redisTemplate);
        // ReflectionTestUtils sets @Value fields that Spring normally injects.
        // Without Spring context, we set them directly.
        ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3600000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604800000L);
    }

    @Test
    @DisplayName("Should generate access token with correct UUID and role")
    void shouldGenerateAccessToken() {
        String token = jwtService.generateAccessToken(
                TEST_UUID, TEST_EMAIL, TEST_ROLE);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(jwtService.extractUserUuid(token)).isEqualTo(TEST_UUID);
        assertThat(jwtService.extractRole(token)).isEqualTo(TEST_ROLE);
    }

    @Test
    @DisplayName("Should generate refresh token with correct UUID")
    void shouldGenerateRefreshToken() {
        String token = jwtService.generateRefreshToken(TEST_UUID);

        assertThat(token).isNotNull();
        assertThat(jwtService.extractUserUuid(token)).isEqualTo(TEST_UUID);
    }

    @Test
    @DisplayName("Should return true for valid non-blacklisted token")
    void shouldValidateFreshToken() {
        String token = jwtService.generateAccessToken(
                TEST_UUID, TEST_EMAIL, TEST_ROLE);

        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("Should return false for blacklisted token")
    void shouldRejectBlacklistedToken() {
        String token = jwtService.generateAccessToken(
                TEST_UUID, TEST_EMAIL, TEST_ROLE);

        when(redisTemplate.hasKey("blacklist:" + token)).thenReturn(true);

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("Should store token in Redis when blacklisting")
    void shouldBlacklistToken() {
        String token = jwtService.generateAccessToken(
                TEST_UUID, TEST_EMAIL, TEST_ROLE);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        jwtService.blacklistToken(token);

        verify(valueOperations).set(
                startsWith("blacklist:"),
                eq("revoked"),
                anyLong(),
                eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("Should not throw if Redis fails during blacklist")
    void shouldHandleRedisFailureGracefully() {
        String token = jwtService.generateAccessToken(
                TEST_UUID, TEST_EMAIL, TEST_ROLE);

        when(redisTemplate.opsForValue())
                .thenThrow(new RuntimeException("Redis down"));

        // Logout must succeed even if Redis is down
        jwtService.blacklistToken(token);
        // No exception = test passes
    }

    @Test
    @DisplayName("Should return false for garbage token string")
    void shouldRejectGarbageToken() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        assertThat(jwtService.isTokenValid("this.is.garbage")).isFalse();
    }
}