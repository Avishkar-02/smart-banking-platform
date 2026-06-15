package com.smartbanking.frauddetectionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbanking.common.event.FraudResultEvent;
import com.smartbanking.common.event.TransactionInitiatedEvent;
import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.frauddetectionservice.entity.FraudEvaluation;
import com.smartbanking.frauddetectionservice.repository.FraudAlertRepository;
import com.smartbanking.frauddetectionservice.repository.FraudEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FraudDetectionService Unit Tests")
class FraudDetectionServiceTest {

    @Mock private RiskScoringEngine riskScoringEngine;
    @Mock private FraudEvaluationRepository evaluationRepository;
    @Mock private FraudAlertRepository alertRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private FraudDetectionService fraudDetectionService;

    private static final String TXN_REF  = "TXN-TEST-001";
    private static final String USER_UUID = "user-uuid-123";

    @BeforeEach
    void setUp() {
        // Inject @Value fields
        ReflectionTestUtils.setField(fraudDetectionService,
                "highRiskThreshold", 70);
        ReflectionTestUtils.setField(fraudDetectionService,
                "mediumRiskThreshold", 40);
        // Inject ObjectMapper
        ReflectionTestUtils.setField(fraudDetectionService,
                "objectMapper", new ObjectMapper());
    }

    private TransactionInitiatedEvent buildEvent() {
        return TransactionInitiatedEvent.builder()
                .transactionRef(TXN_REF)
                .sourceAccountNumber("SBP0000000001")
                .destinationAccountNumber("SBP0000000002")
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .initiatedByUuid(USER_UUID)
                .build();
    }

    // Helper to build a ScoreResult
    private RiskScoringEngine.ScoreResult buildScore(int total) {
        Map<String, Integer> breakdown = Map.of(
                "HIGH_AMOUNT", total >= 40 ? 40 : 0,
                "HIGH_VELOCITY", 0,
                "UNUSUAL_HOUR", 0,
                "RAPID_SAME_DEST", 0
        );
        return new RiskScoringEngine.ScoreResult(total, breakdown);
    }

    // ── APPROVED (clean) ────────────────────────────────────────────────────

    @Test
    @DisplayName("Low-risk transaction should be APPROVED with no alert")
    void shouldApproveCleanTransaction() {
        when(evaluationRepository.existsByTransactionRef(TXN_REF)).thenReturn(false);
        when(riskScoringEngine.calculateScore(any())).thenReturn(buildScore(10));
        when(evaluationRepository.save(any())).thenReturn(new FraudEvaluation());

        fraudDetectionService.evaluate(buildEvent());

        // Verify FraudResultEvent published with APPROVED decision
        ArgumentCaptor<FraudResultEvent> captor =
                ArgumentCaptor.forClass(FraudResultEvent.class);
        verify(kafkaTemplate).send(
                eq("fraud-detection-service.fraud.result"),
                eq(TXN_REF),
                captor.capture());

        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVED");
        assertThat(captor.getValue().getRiskScore()).isEqualTo(10);
        assertThat(captor.getValue().isRequiresManualReview()).isFalse();

        // No alert should be created for clean approval
        verify(alertRepository, never()).save(any());
    }

    // ── APPROVED (flagged for review) ───────────────────────────────────────

    @Test
    @DisplayName("Medium-risk transaction (score 50) should be APPROVED with manual review flag")
    void shouldApproveMediumRiskWithManualReview() {
        when(evaluationRepository.existsByTransactionRef(TXN_REF)).thenReturn(false);
        when(riskScoringEngine.calculateScore(any())).thenReturn(buildScore(50));
        when(evaluationRepository.save(any())).thenReturn(new FraudEvaluation());
        when(alertRepository.save(any())).thenReturn(new com.smartbanking.frauddetectionservice.entity.FraudAlert());

        fraudDetectionService.evaluate(buildEvent());

        ArgumentCaptor<FraudResultEvent> captor =
                ArgumentCaptor.forClass(FraudResultEvent.class);
        verify(kafkaTemplate).send(
                eq("fraud-detection-service.fraud.result"),
                eq(TXN_REF),
                captor.capture());

        // APPROVED but with manual review flag
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVED");
        assertThat(captor.getValue().isRequiresManualReview()).isTrue();

        // Alert SHOULD be created for medium-risk
        verify(alertRepository, times(1)).save(any());

        // FraudAlertEvent SHOULD be published
        verify(kafkaTemplate).send(
                eq("fraud-detection-service.fraud.alert"),
                eq(TXN_REF),
                any());
    }

    // ── BLOCKED ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("High-risk transaction (score 90) should be BLOCKED")
    void shouldBlockHighRiskTransaction() {
        when(evaluationRepository.existsByTransactionRef(TXN_REF)).thenReturn(false);
        when(riskScoringEngine.calculateScore(any())).thenReturn(buildScore(90));
        when(evaluationRepository.save(any())).thenReturn(new FraudEvaluation());
        when(alertRepository.save(any())).thenReturn(new com.smartbanking.frauddetectionservice.entity.FraudAlert());

        fraudDetectionService.evaluate(buildEvent());

        ArgumentCaptor<FraudResultEvent> captor =
                ArgumentCaptor.forClass(FraudResultEvent.class);
        verify(kafkaTemplate).send(
                eq("fraud-detection-service.fraud.result"),
                eq(TXN_REF),
                captor.capture());

        assertThat(captor.getValue().getDecision()).isEqualTo("BLOCKED");
        assertThat(captor.getValue().getRiskScore()).isEqualTo(90);
    }

    // ── IDEMPOTENCY ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Duplicate event (same transactionRef) should be skipped entirely")
    void shouldSkipDuplicateEvent() {
        // Transaction already evaluated
        when(evaluationRepository.existsByTransactionRef(TXN_REF)).thenReturn(true);

        fraudDetectionService.evaluate(buildEvent());

        // Nothing should happen for duplicate
        verify(riskScoringEngine, never()).calculateScore(any());
        verify(evaluationRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    // ── QUERY METHODS ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEvaluationByTransactionRef should throw when not found")
    void shouldThrowWhenEvaluationNotFound() {
        when(evaluationRepository.findByTransactionRef(TXN_REF))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                fraudDetectionService.getEvaluationByTransactionRef(TXN_REF))
                .isInstanceOf(BusinessException.class);
    }
}