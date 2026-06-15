package com.smartbanking.frauddetectionservice.service;

import com.smartbanking.common.event.TransactionInitiatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

// RiskScoringEngine is the pure scoring logic.
// It runs each rule independently, accumulates points,
// and returns a ScoreResult with the total and a breakdown.
//
// Design principle: each rule is a private method.
// Adding a new rule = adding one private method and calling it in calculateScore().
// Changing a threshold = change the @Value property in config-server.
// Zero code change needed for threshold adjustments.

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskScoringEngine {

    private final VelocityTrackerService velocityTracker;

    // All thresholds injected from config-server via @Value.
    // Change fraud.rules.* in config-server's fraud-detection-service.properties.
    // Restart fraud-detection-service. New rules take effect immediately.

    @Value("${fraud.rules.max-amount-threshold:100000}")
    private BigDecimal maxAmountThreshold;

    @Value("${fraud.rules.max-transactions-per-minute:5}")
    private int maxTransactionsPerMinute;

    @Value("${fraud.rules.max-same-destination-per-hour:3}")
    private int maxSameDestinationPerHour;

    @Value("${fraud.rules.unusual-hour-start:1}")
    private int unusualHourStart;

    @Value("${fraud.rules.unusual-hour-end:5}")
    private int unusualHourEnd;

    // The result of a scoring evaluation.
    // Contains the total score AND a breakdown map for audit logging.
    public record ScoreResult(int totalScore, Map<String, Integer> breakdown) {

        // Convenience: is this score high enough to block?
        public boolean isHighRisk(int threshold) {
            return totalScore >= threshold;
        }

        // Builds the human-readable description for the FraudAlert
        public String buildDescription(BigDecimal amount, String userUuid) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Risk score: %d. ", totalScore));
            breakdown.forEach((rule, points) -> {
                if (points > 0) {
                    sb.append(String.format("%s (+%d pts). ", rule, points));
                }
            });
            return sb.toString().trim();
        }

        // Determines the primary alert type for the FraudAlert entity
        public String getPrimaryAlertType() {
            return breakdown.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("COMBINED");
        }
    }

    // Main entry point — evaluates all rules and returns the total score.
    // The VelocityTracker INCREMENTS counters as part of this call.
    // This means: calling calculateScore() is NOT idempotent for velocity rules.
    // The consumer must ensure it only calls this ONCE per transaction.
    public ScoreResult calculateScore(TransactionInitiatedEvent event) {

        log.debug("Calculating risk score for transaction: {}, amount: {}, user: {}",
                event.getTransactionRef(), event.getAmount(),
                event.getInitiatedByUuid());

        // LinkedHashMap preserves insertion order — breakdown appears consistently
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        int totalScore = 0;

        // ── Rule 1: High Amount ────────────────────────────────────────────
        // Simple threshold check — no Redis needed, pure calculation.
        int amountPoints = checkHighAmount(event.getAmount());
        breakdown.put("HIGH_AMOUNT", amountPoints);
        totalScore += amountPoints;

        // ── Rule 2: High Velocity (transactions per minute) ───────────────
        // INCREMENTS the Redis counter for this user.
        // Returns how many transactions this user has made in the last 60 seconds
        // INCLUDING this one (because we incremented).
        int velocityPoints = checkHighVelocity(event.getInitiatedByUuid());
        breakdown.put("HIGH_VELOCITY", velocityPoints);
        totalScore += velocityPoints;

        // ── Rule 3: Unusual Hour ───────────────────────────────────────────
        // Pure time calculation — no external dependencies.
        int hourPoints = checkUnusualHour();
        breakdown.put("UNUSUAL_HOUR", hourPoints);
        totalScore += hourPoints;

        // ── Rule 4: Rapid Same Destination ────────────────────────────────
        // INCREMENTS the destination-specific Redis counter.
        int destPoints = checkRapidSameDestination(
                event.getInitiatedByUuid(),
                event.getDestinationAccountNumber());
        breakdown.put("RAPID_SAME_DEST", destPoints);
        totalScore += destPoints;

        // Cap score at 100 for clean display — but internally store actual score
        // so we can see exactly how many rules fired (e.g. 135 = ALL rules fired)
        log.info("Risk score calculated — ref: {}, score: {}, breakdown: {}",
                event.getTransactionRef(), totalScore, breakdown);

        return new ScoreResult(totalScore, breakdown);
    }

    // ── Private rule methods ──────────────────────────────────────────────

    // Rule 1: Is the amount above our threshold?
    // compareTo returns: negative if amount < threshold, zero if equal, positive if greater
    private int checkHighAmount(BigDecimal amount) {
        if (amount.compareTo(maxAmountThreshold) > 0) {
            log.debug("HIGH_AMOUNT rule fired: {} > threshold {}",
                    amount, maxAmountThreshold);
            return 40;
        }
        return 0;
    }

    // Rule 2: Has this user made too many transactions in the last minute?
    // We INCREMENT the counter here — this call is NOT idempotent.
    private int checkHighVelocity(String userUuid) {
        long count = velocityTracker
                .incrementAndGetUserTransactionCount(userUuid);

        if (count > maxTransactionsPerMinute) {
            log.debug("HIGH_VELOCITY rule fired: user {} made {} txns in 60s " +
                    "(threshold: {})", userUuid, count, maxTransactionsPerMinute);
            return 50;
        }
        return 0;
    }

    // Rule 3: Is this transaction happening during unusual hours?
    // 1am-5am = higher risk — most legitimate users are asleep
    private int checkUnusualHour() {
        int currentHour = LocalTime.now().getHour();
        if (currentHour >= unusualHourStart && currentHour < unusualHourEnd) {
            log.debug("UNUSUAL_HOUR rule fired: current hour = {}", currentHour);
            return 15;
        }
        return 0;
    }

    // Rule 4: Is this user sending rapidly to the same destination?
    // Could indicate a fraudster making multiple small transfers to avoid detection
    private int checkRapidSameDestination(String userUuid,
                                          String destinationAccountNumber) {
        long count = velocityTracker.incrementAndGetSameDestinationCount(
                userUuid, destinationAccountNumber);

        if (count > maxSameDestinationPerHour) {
            log.debug("RAPID_SAME_DEST rule fired: user {} sent to {} {} times " +
                            "in 1 hour (threshold: {})",
                    userUuid, destinationAccountNumber, count,
                    maxSameDestinationPerHour);
            return 30;
        }
        return 0;
    }
}