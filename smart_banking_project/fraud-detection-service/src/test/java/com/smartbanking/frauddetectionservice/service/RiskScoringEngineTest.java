package com.smartbanking.frauddetectionservice.service;

import com.smartbanking.common.event.TransactionInitiatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

// Tests the risk scoring logic — each rule independently and in combination.
// VelocityTrackerService is mocked so we control exactly what counts Redis returns.

@ExtendWith(MockitoExtension.class)
@DisplayName("RiskScoringEngine Unit Tests")
class RiskScoringEngineTest {

    @Mock private VelocityTrackerService velocityTracker;
    @InjectMocks private RiskScoringEngine riskScoringEngine;

    @BeforeEach
    void setUp() {
        // Inject @Value fields manually — no Spring context in unit tests
        ReflectionTestUtils.setField(riskScoringEngine, "maxAmountThreshold",
                new BigDecimal("100000"));
        ReflectionTestUtils.setField(riskScoringEngine, "maxTransactionsPerMinute", 5);
        ReflectionTestUtils.setField(riskScoringEngine, "maxSameDestinationPerHour", 3);
        ReflectionTestUtils.setField(riskScoringEngine, "unusualHourStart", 1);
        ReflectionTestUtils.setField(riskScoringEngine, "unusualHourEnd", 5);
    }

    // Helper to build a standard test event
    private TransactionInitiatedEvent buildEvent(BigDecimal amount) {
        return TransactionInitiatedEvent.builder()
                .transactionRef("TXN-TEST-001")
                .sourceAccountNumber("SBP0000000001")
                .destinationAccountNumber("SBP0000000002")
                .amount(amount)
                .currency("INR")
                .initiatedByUuid("user-uuid-123")
                .build();
    }

    @Test
    @DisplayName("Normal transaction under all thresholds should score 0 (ignoring time)")
    void shouldScoreZeroForNormalTransaction() {
        // Under amount threshold, low velocity, low same-destination count
        when(velocityTracker.incrementAndGetUserTransactionCount(anyString()))
                .thenReturn(1L);
        when(velocityTracker.incrementAndGetSameDestinationCount(
                anyString(), anyString())).thenReturn(1L);

        TransactionInitiatedEvent event = buildEvent(new BigDecimal("1000"));
        RiskScoringEngine.ScoreResult result = riskScoringEngine.calculateScore(event);

        // Score is 0 for amount and velocity rules
        // Time-based rule may add 15 if running at 1-5am — we cannot mock LocalTime
        // So we check that amount and velocity contributed 0
        assertThat(result.breakdown().get("HIGH_AMOUNT")).isEqualTo(0);
        assertThat(result.breakdown().get("HIGH_VELOCITY")).isEqualTo(0);
        assertThat(result.breakdown().get("RAPID_SAME_DEST")).isEqualTo(0);
    }

    @Test
    @DisplayName("Amount above threshold (₹150,000) should add 40 points")
    void shouldAdd40PointsForHighAmount() {
        when(velocityTracker.incrementAndGetUserTransactionCount(anyString()))
                .thenReturn(1L);
        when(velocityTracker.incrementAndGetSameDestinationCount(
                anyString(), anyString())).thenReturn(1L);

        TransactionInitiatedEvent event = buildEvent(new BigDecimal("150000"));
        RiskScoringEngine.ScoreResult result = riskScoringEngine.calculateScore(event);

        assertThat(result.breakdown().get("HIGH_AMOUNT")).isEqualTo(40);
    }

    @Test
    @DisplayName("6 transactions in 60 seconds should add 50 points for HIGH_VELOCITY")
    void shouldAdd50PointsForHighVelocity() {
        // 6 > threshold of 5 → velocity rule fires
        when(velocityTracker.incrementAndGetUserTransactionCount(anyString()))
                .thenReturn(6L);
        when(velocityTracker.incrementAndGetSameDestinationCount(
                anyString(), anyString())).thenReturn(1L);

        TransactionInitiatedEvent event = buildEvent(new BigDecimal("500"));
        RiskScoringEngine.ScoreResult result = riskScoringEngine.calculateScore(event);

        assertThat(result.breakdown().get("HIGH_VELOCITY")).isEqualTo(50);
    }

    @Test
    @DisplayName("4 same-destination transfers in 1 hour should add 30 points")
    void shouldAdd30PointsForRapidSameDestination() {
        when(velocityTracker.incrementAndGetUserTransactionCount(anyString()))
                .thenReturn(1L);
        // 4 > threshold of 3 → same-destination rule fires
        when(velocityTracker.incrementAndGetSameDestinationCount(
                anyString(), anyString())).thenReturn(4L);

        TransactionInitiatedEvent event = buildEvent(new BigDecimal("500"));
        RiskScoringEngine.ScoreResult result = riskScoringEngine.calculateScore(event);

        assertThat(result.breakdown().get("RAPID_SAME_DEST")).isEqualTo(30);
    }

    @Test
    @DisplayName("High amount + high velocity should score 90 → BLOCKED threshold")
    void shouldScoreAboveBlockThresholdWhenMultipleRulesFire() {
        when(velocityTracker.incrementAndGetUserTransactionCount(anyString()))
                .thenReturn(6L);   // HIGH_VELOCITY +50
        when(velocityTracker.incrementAndGetSameDestinationCount(
                anyString(), anyString())).thenReturn(1L);

        TransactionInitiatedEvent event = buildEvent(new BigDecimal("150000")); // HIGH_AMOUNT +40

        RiskScoringEngine.ScoreResult result = riskScoringEngine.calculateScore(event);

        // 40 + 50 = 90 (ignoring possible UNUSUAL_HOUR +15)
        assertThat(result.breakdown().get("HIGH_AMOUNT")).isEqualTo(40);
        assertThat(result.breakdown().get("HIGH_VELOCITY")).isEqualTo(50);
        // Total should be at least 90 (may be 105 if running between 1-5am)
        assertThat(result.totalScore()).isGreaterThanOrEqualTo(90);
        // Should definitely be blocked
        assertThat(result.isHighRisk(70)).isTrue();
    }

    @Test
    @DisplayName("Exactly at threshold (₹100,000) should NOT trigger HIGH_AMOUNT")
    void shouldNotTriggerHighAmountAtExactThreshold() {
        when(velocityTracker.incrementAndGetUserTransactionCount(anyString()))
                .thenReturn(1L);
        when(velocityTracker.incrementAndGetSameDestinationCount(
                anyString(), anyString())).thenReturn(1L);

        // Exactly 100,000 — our rule is GREATER THAN, not greater than or equal
        TransactionInitiatedEvent event = buildEvent(new BigDecimal("100000"));
        RiskScoringEngine.ScoreResult result = riskScoringEngine.calculateScore(event);

        assertThat(result.breakdown().get("HIGH_AMOUNT")).isEqualTo(0);
    }

    @Test
    @DisplayName("ScoreResult.getPrimaryAlertType returns rule with highest points")
    void shouldReturnPrimaryAlertType() {
        when(velocityTracker.incrementAndGetUserTransactionCount(anyString()))
                .thenReturn(6L);   // HIGH_VELOCITY +50 (highest)
        when(velocityTracker.incrementAndGetSameDestinationCount(
                anyString(), anyString())).thenReturn(1L);

        TransactionInitiatedEvent event = buildEvent(new BigDecimal("150000")); // HIGH_AMOUNT +40
        RiskScoringEngine.ScoreResult result = riskScoringEngine.calculateScore(event);

        // HIGH_VELOCITY contributed 50 points — most, so it is primary type
        assertThat(result.getPrimaryAlertType()).isEqualTo("HIGH_VELOCITY");
    }
}