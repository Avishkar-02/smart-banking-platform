package com.smartbanking.frauddetectionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Pure unit test — no Spring, no real Redis.
// Tests the INCR + EXPIRE logic in isolation.

@ExtendWith(MockitoExtension.class)
@DisplayName("VelocityTrackerService Unit Tests")
class VelocityTrackerServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private VelocityTrackerService velocityTracker;

    private static final String USER_UUID = "user-uuid-123";
    private static final String DEST_ACC  = "SBP0000000002";

    @BeforeEach
    void setUp() {
        velocityTracker = new VelocityTrackerService(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("First transaction: INCR returns 1, EXPIRE is set")
    void shouldSetTtlOnFirstIncrement() {
        // Redis returns 1 — this is the first transaction in the window
        when(valueOperations.increment(anyString())).thenReturn(1L);

        long count = velocityTracker
                .incrementAndGetUserTransactionCount(USER_UUID);

        assertThat(count).isEqualTo(1L);
        // EXPIRE must be set when count == 1 — starts the time window
        verify(redisTemplate).expire(
                eq("velocity:user:" + USER_UUID + ":60s"),
                eq(60L),
                eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Subsequent transactions: INCR increments, EXPIRE NOT reset")
    void shouldNotResetTtlOnSubsequentIncrements() {
        // Redis returns 3 — third transaction in this window
        when(valueOperations.increment(anyString())).thenReturn(3L);

        long count = velocityTracker
                .incrementAndGetUserTransactionCount(USER_UUID);

        assertThat(count).isEqualTo(3L);
        // EXPIRE must NOT be called when count > 1 — window already started
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Should return 0 and not throw when Redis is down")
    void shouldFailOpenWhenRedisDown() {
        // Redis throws exception — simulates Redis being down
        when(valueOperations.increment(anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // Should not throw — should return 0 (fail open)
        long count = velocityTracker
                .incrementAndGetUserTransactionCount(USER_UUID);

        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("Same destination counter uses correct key with 3600s TTL")
    void shouldUseCorrectKeyForSameDestination() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        long count = velocityTracker
                .incrementAndGetSameDestinationCount(USER_UUID, DEST_ACC);

        assertThat(count).isEqualTo(1L);
        // Verify the correct key pattern and 1-hour TTL
        verify(valueOperations).increment(
                "velocity:user:" + USER_UUID + ":dest:" + DEST_ACC + ":3600s");
        verify(redisTemplate).expire(
                eq("velocity:user:" + USER_UUID + ":dest:" + DEST_ACC + ":3600s"),
                eq(3600L),
                eq(TimeUnit.SECONDS));
    }
}