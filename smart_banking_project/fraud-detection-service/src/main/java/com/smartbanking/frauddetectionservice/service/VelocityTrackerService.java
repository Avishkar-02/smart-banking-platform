package com.smartbanking.frauddetectionservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

// VelocityTrackerService is the Redis INCR/EXPIRE wrapper.
// It answers one question: "How many times has X happened in the last N seconds?"
// Used by RiskScoringEngine to check transaction frequency rules.
//
// The pattern: INCR the counter on every event. Set EXPIRE only on the
// FIRST increment (count == 1). This means the window starts from the
// FIRST event and expires N seconds later.

@Slf4j
@Service
@RequiredArgsConstructor
public class VelocityTrackerService {

    private final RedisTemplate<String, String> redisTemplate;

    // Key prefixes — consistent naming for easy inspection and debugging
    private static final String USER_TXN_COUNT_PREFIX = "velocity:user:";
    private static final String DEST_COUNT_SUFFIX     = ":dest:";
    private static final String TXN_WINDOW_SUFFIX     = ":60s";
    private static final String HOUR_WINDOW_SUFFIX    = ":3600s";

    // ── User transaction velocity (60-second window) ─────────────────────

    // Called every time a transaction is initiated by a user.
    // Returns the CURRENT count AFTER incrementing.
    // So if this is the 6th transaction, returns 6.
    public long incrementAndGetUserTransactionCount(String userUuid) {
        String key = USER_TXN_COUNT_PREFIX + userUuid + TXN_WINDOW_SUFFIX;
        return incrementWithTtl(key, 60, TimeUnit.SECONDS);
    }

    // Returns current count without incrementing.
    // Used for checking the rule without affecting the counter.
    public long getUserTransactionCount(String userUuid) {
        String key = USER_TXN_COUNT_PREFIX + userUuid + TXN_WINDOW_SUFFIX;
        return getCurrentCount(key);
    }

    // ── Same-destination velocity (1-hour window) ─────────────────────────

    // Tracks how many times a user sent to the same destination in 1 hour.
    // Key includes both userUuid and destination — unique per user-destination pair.
    public long incrementAndGetSameDestinationCount(
            String userUuid, String destinationAccountNumber) {
        String key = USER_TXN_COUNT_PREFIX + userUuid
                + DEST_COUNT_SUFFIX + destinationAccountNumber + HOUR_WINDOW_SUFFIX;
        return incrementWithTtl(key, 3600, TimeUnit.SECONDS);
    }

    // ── Core Redis INCR + EXPIRE logic ────────────────────────────────────

    // Atomically increments a counter and sets TTL on first increment.
    // Returns the new count after incrementing.
    private long incrementWithTtl(String key, long ttl, TimeUnit unit) {
        try {
            // INCR is atomic — thread-safe even under concurrent requests.
            // If key does not exist, Redis creates it with value 0 then increments to 1.
            Long count = redisTemplate.opsForValue().increment(key);

            if (count == null) {
                log.warn("Redis INCR returned null for key: {}", key);
                return 0L;
            }

            // Set TTL only on the FIRST increment.
            // Why? If we set TTL on every increment, the window keeps resetting.
            // We want: "5 transactions in a fixed 60-second window"
            // Not: "5 transactions where each one extends the window by 60 seconds"
            if (count == 1L) {
                redisTemplate.expire(key, ttl, unit);
                log.debug("New velocity window started — key: {}, TTL: {}{}",
                        key, ttl, unit.toString().toLowerCase());
            }

            log.debug("Velocity count for key: {} = {}", key, count);
            return count;

        } catch (Exception e) {
            // Redis is down. Return 0 — fail open.
            // Velocity rules will not fire. Other rules (amount, time) still work.
            // Logging the error alerts the ops team to fix Redis.
            log.error("Redis error during velocity tracking for key: {}. " +
                    "Failing open — returning 0.", key, e);
            return 0L;
        }
    }

    // Returns the current count for a key without modifying it.
    private long getCurrentCount(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return 0L;
            }
            return Long.parseLong(value);
        } catch (Exception e) {
            log.error("Redis error reading velocity count for key: {}", key, e);
            return 0L;
        }
    }
}