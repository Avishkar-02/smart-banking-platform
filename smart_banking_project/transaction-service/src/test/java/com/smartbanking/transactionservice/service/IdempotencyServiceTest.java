package com.smartbanking.transactionservice.service;

import com.smartbanking.transactionservice.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService Unit Tests")
class IdempotencyServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;

    private static final String KEY = "test-idempotency-key-123";
    private static final String REDIS_KEY = "idempotency:" + KEY;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redisTemplate);
    }

    @Test
    @DisplayName("Should return empty when key is not in Redis (new request)")
    void shouldReturnEmptyOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);

        Optional<TransactionResponse> result =
                idempotencyService.getCachedResponse(KEY);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return cached response when key exists in Redis")
    void shouldReturnCachedResponseOnHit() {
        // A valid JSON string that Jackson can deserialize
        String json = "{\"uuid\":\"test-uuid\",\"transactionRef\":\"TXN-123\"," +
                "\"sourceAccountNumber\":\"SBP0000000001\"," +
                "\"destinationAccountNumber\":\"SBP0000000002\"," +
                "\"amount\":5000.00,\"currency\":\"INR\"," +
                "\"status\":\"PENDING\"}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn(json);

        Optional<TransactionResponse> result =
                idempotencyService.getCachedResponse(KEY);

        assertThat(result).isPresent();
        assertThat(result.get().getTransactionRef()).isEqualTo("TXN-123");
    }

    @Test
    @DisplayName("Should store response in Redis with 24h TTL after processing")
    void shouldCacheResponse() {
        TransactionResponse response = TransactionResponse.builder()
                .uuid("test-uuid")
                .transactionRef("TXN-123")
                .sourceAccountNumber("SBP0000000001")
                .destinationAccountNumber("SBP0000000002")
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .status("PENDING")
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        idempotencyService.cacheResponse(KEY, response);

        // Verify SET was called with 24h TTL
        verify(valueOperations).set(
                eq(REDIS_KEY),
                anyString(),
                eq(24L),
                eq(TimeUnit.HOURS));
    }

    @Test
    @DisplayName("Should handle corrupted cache entry gracefully")
    void shouldHandleCorruptedCacheEntry() {
        // Invalid JSON in Redis — corruption scenario
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(REDIS_KEY)).thenReturn("not valid json at all {{{");

        // Should not throw — should return empty and delete corrupt entry
        Optional<TransactionResponse> result =
                idempotencyService.getCachedResponse(KEY);

        assertThat(result).isEmpty();
        verify(redisTemplate).delete(REDIS_KEY);
    }
}