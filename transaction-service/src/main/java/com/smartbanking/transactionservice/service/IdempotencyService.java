package com.smartbanking.transactionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.smartbanking.transactionservice.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

// IdempotencyService is the gatekeeper.
// Every transfer request passes through it before any processing happens.
// If the key was already processed, return the cached result immediately.
// If not, process normally and store the result afterward.

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    // ObjectMapper converts TransactionResponse ↔ JSON string for Redis storage.
    // We use a single instance — ObjectMapper is thread-safe and expensive to create.
    private final ObjectMapper objectMapper = new ObjectMapper()
            // JavaTimeModule handles LocalDateTime serialization.
            // Without this, Jackson throws: "Java 8 date/time type not supported by default"
            .registerModule(new JavaTimeModule());

    // Redis key prefix — groups all idempotency keys together.
    // Easy to inspect: KEYS idempotency:* in Redis CLI
    private static final String IDEMPOTENCY_PREFIX = "idempotency:";

    // 24 hours TTL — client can safely retry within 24 hours and get same result.
    // After 24 hours, Redis auto-deletes the key.
    // A new request with the same key after 24 hours would be treated as a new transfer.
    private static final long TTL_HOURS = 24;

    // Checks if this idempotency key was already processed.
    // Returns the cached TransactionResponse if found.
    // Returns empty Optional if this is a new request.
    public Optional<TransactionResponse> getCachedResponse(String idempotencyKey) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String cachedJson = redisTemplate.opsForValue().get(redisKey);

        if (cachedJson == null) {
            log.debug("Idempotency cache MISS for key: {}", idempotencyKey);
            return Optional.empty();
        }

        log.info("Idempotency cache HIT for key: {} — returning cached response",
                idempotencyKey);

        try {
            TransactionResponse response = objectMapper.readValue(
                    cachedJson, TransactionResponse.class);
            return Optional.of(response);
        } catch (JsonProcessingException e) {
            // Deserialization failed — cache is corrupted.
            // Delete the bad entry and treat as a new request.
            // This prevents permanently broken idempotency for this key.
            log.error("Failed to deserialize cached response for key: {}. " +
                    "Treating as new request.", idempotencyKey, e);
            redisTemplate.delete(redisKey);
            return Optional.empty();
        }
    }

    // Stores the response in Redis after successful processing.
    // Called AFTER the transaction is saved to DB and saga is started.
    // The stored response is what future duplicate requests will receive.
    public void cacheResponse(String idempotencyKey, TransactionResponse response) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, json, TTL_HOURS, TimeUnit.HOURS);
            log.info("Idempotency response cached for key: {} TTL: {}h",
                    idempotencyKey, TTL_HOURS);
        } catch (JsonProcessingException e) {
            // Serialization failure. Log and continue — transaction already saved.
            // Next duplicate request will miss the cache and be rejected by
            // the DB unique constraint on idempotency_key column.
            log.error("Failed to cache idempotency response for key: {}",
                    idempotencyKey, e);
        }
    }

    // Updates the cached response when transaction status changes.
    // Called by SagaEventListener when saga reaches terminal state.
    // This ensures the client always gets the final status on retries.
    public void updateCachedResponse(String idempotencyKey,
                                     TransactionResponse updatedResponse) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        // Only update if the key still exists — don't recreate expired entries
        Boolean keyExists = redisTemplate.hasKey(redisKey);
        if (Boolean.TRUE.equals(keyExists)) {
            try {
                String json = objectMapper.writeValueAsString(updatedResponse);
                // Preserve remaining TTL — do not reset to 24 hours
                // getExpire returns remaining TTL in seconds
                Long remainingTtl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
                if (remainingTtl != null && remainingTtl > 0) {
                    redisTemplate.opsForValue().set(
                            redisKey, json, remainingTtl, TimeUnit.SECONDS);
                    log.debug("Updated idempotency cache for key: {}", idempotencyKey);
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to update cached idempotency response: {}",
                        idempotencyKey, e);
            }
        }
    }
}