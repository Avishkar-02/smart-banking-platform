package com.smartbanking.frauddetectionservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbanking.common.event.FraudAlertEvent;
import com.smartbanking.common.event.FraudResultEvent;
import com.smartbanking.common.event.TransactionInitiatedEvent;
import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.common.exception.ErrorCode;
import com.smartbanking.frauddetectionservice.dto.FraudAlertResponse;
import com.smartbanking.frauddetectionservice.dto.FraudEvaluationResponse;
import com.smartbanking.frauddetectionservice.entity.FraudAlert;
import com.smartbanking.frauddetectionservice.entity.FraudEvaluation;
import com.smartbanking.frauddetectionservice.repository.FraudAlertRepository;
import com.smartbanking.frauddetectionservice.repository.FraudEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final RiskScoringEngine riskScoringEngine;
    private final FraudEvaluationRepository evaluationRepository;
    private final FraudAlertRepository alertRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${fraud.rules.high-risk-score-threshold:70}")
    private int highRiskThreshold;

    @Value("${fraud.rules.medium-risk-score-threshold:40}")
    private int mediumRiskThreshold;

    // ── Core Evaluation ────────────────────────────────────────────────────

    // This is the main method called by the Kafka consumer for every
    // TransactionInitiatedEvent. It orchestrates the full fraud evaluation.
    //
    // @Transactional ensures: if DB save fails, the Kafka publish also does not
    // happen (because the transaction rolls back before we reach kafkaTemplate.send).
    // This prevents publishing a fraud result without saving the audit record.
    @Transactional
    public void evaluate(TransactionInitiatedEvent event) {

        log.info("Starting fraud evaluation — ref: {}, amount: {}, user: {}",
                event.getTransactionRef(), event.getAmount(),
                event.getInitiatedByUuid());

        // ── Idempotency check ──────────────────────────────────────────────
        // Kafka guarantees at-least-once delivery.
        // The same event might be delivered twice (e.g. after a consumer restart).
        // We check if we already evaluated this transaction before processing.
        if (evaluationRepository.existsByTransactionRef(
                event.getTransactionRef())) {
            log.warn("Duplicate TransactionInitiatedEvent received for ref: {}. " +
                    "Skipping — already evaluated.", event.getTransactionRef());
            return;
        }

        // ── Score the transaction ──────────────────────────────────────────
        RiskScoringEngine.ScoreResult scoreResult =
                riskScoringEngine.calculateScore(event);

        int score = scoreResult.totalScore();
        boolean isBlocked = score >= highRiskThreshold;
        boolean requiresReview = !isBlocked && score >= mediumRiskThreshold;
        String decision = isBlocked ? "BLOCKED" : "APPROVED";

        log.info("Fraud evaluation complete — ref: {}, score: {}, decision: {}",
                event.getTransactionRef(), score, decision);

        // ── Save evaluation audit record ───────────────────────────────────
        String breakdownJson = toJson(scoreResult.breakdown());

        FraudEvaluation evaluation = FraudEvaluation.builder()
                .transactionRef(event.getTransactionRef())
                .userUuid(event.getInitiatedByUuid())
                .sourceAccountNumber(event.getSourceAccountNumber())
                .destinationAccountNumber(event.getDestinationAccountNumber())
                .amount(event.getAmount())
                .riskScore(score)
                .decision(decision)
                .scoreBreakdown(breakdownJson)
                .requiresManualReview(requiresReview)
                .build();
        // uuid and evaluatedAt set by @PrePersist

        evaluationRepository.save(evaluation);
        log.debug("FraudEvaluation saved for ref: {}", event.getTransactionRef());

        // ── Save alert if score is elevated ───────────────────────────────
        if (score >= mediumRiskThreshold) {
            String alertType = scoreResult.getPrimaryAlertType();
            String alertDecision = isBlocked ? "BLOCKED" : "FLAGGED";
            String description = scoreResult.buildDescription(
                    event.getAmount(), event.getInitiatedByUuid());

            FraudAlert alert = FraudAlert.builder()
                    .transactionRef(event.getTransactionRef())
                    .userUuid(event.getInitiatedByUuid())
                    .riskScore(score)
                    .alertType(alertType)
                    .decision(alertDecision)
                    .description(description)
                    .resolved(false)
                    .build();

            alertRepository.save(alert);
            log.info("FraudAlert created — ref: {}, type: {}, decision: {}",
                    event.getTransactionRef(), alertType, alertDecision);

            // Publish FraudAlertEvent for notification-service
            FraudAlertEvent alertEvent = FraudAlertEvent.builder()
                    .transactionRef(event.getTransactionRef())
                    .userUuid(event.getInitiatedByUuid())
                    .riskScore(score)
                    .alertType(alertType)
                    .description(description)
                    .recommendedAction(isBlocked ? "BLOCK" : "FLAG")
                    .build();

            kafkaTemplate.send(
                    "fraud-detection-service.fraud.alert",
                    event.getTransactionRef(),
                    alertEvent);
        }

        // ── Publish fraud result — this drives the saga ────────────────────
        // This is the most important Kafka publish in this service.
        // transaction-service SagaEventListener is waiting for this.
        FraudResultEvent resultEvent = FraudResultEvent.builder()
                .transactionRef(event.getTransactionRef())
                .decision(decision)
                .riskScore(score)
                .reason(isBlocked ? scoreResult.buildDescription(
                        event.getAmount(), event.getInitiatedByUuid()) : null)
                .requiresManualReview(requiresReview)
                .build();

        kafkaTemplate.send(
                "fraud-detection-service.fraud.result",
                event.getTransactionRef(),
                resultEvent);

        log.info("FraudResultEvent published — ref: {}, decision: {}, score: {}",
                event.getTransactionRef(), decision, score);
    }

    // ── Admin Query Methods ────────────────────────────────────────────────

    public FraudEvaluationResponse getEvaluationByTransactionRef(
            String transactionRef) {
        FraudEvaluation evaluation = evaluationRepository
                .findByTransactionRef(transactionRef)
                .orElseThrow(() -> {
                    log.warn("Fraud evaluation not found for ref: {}",
                            transactionRef);
                    return new BusinessException(
                            ErrorCode.TRANSACTION_NOT_FOUND, HttpStatus.NOT_FOUND,
                            "Fraud evaluation not found for: " + transactionRef);
                });

        return mapEvaluationToResponse(evaluation);
    }

    public List<FraudAlertResponse> getAlertsByUser(String userUuid) {
        log.debug("Fetching fraud alerts for user: {}", userUuid);
        return alertRepository.findByUserUuidOrderByCreatedAtDesc(userUuid)
                .stream()
                .map(this::mapAlertToResponse)
                .collect(Collectors.toList());
    }

    public List<FraudAlertResponse> getRecentAlerts() {
        log.debug("Fetching 50 most recent unresolved fraud alerts");
        // PageRequest.of(0, 50) = first page, 50 results max
        return alertRepository
                .findByResolvedFalseOrderByCreatedAtDesc(PageRequest.of(0, 50))
                .stream()
                .map(this::mapAlertToResponse)
                .collect(Collectors.toList());
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private FraudEvaluationResponse mapEvaluationToResponse(
            FraudEvaluation e) {
        return FraudEvaluationResponse.builder()
                .uuid(e.getUuid())
                .transactionRef(e.getTransactionRef())
                .userUuid(e.getUserUuid())
                .sourceAccountNumber(e.getSourceAccountNumber())
                .destinationAccountNumber(e.getDestinationAccountNumber())
                .amount(e.getAmount())
                .riskScore(e.getRiskScore())
                .decision(e.getDecision())
                .scoreBreakdown(e.getScoreBreakdown())
                .requiresManualReview(e.isRequiresManualReview())
                .evaluatedAt(e.getEvaluatedAt())
                .build();
    }

    private FraudAlertResponse mapAlertToResponse(FraudAlert a) {
        return FraudAlertResponse.builder()
                .uuid(a.getUuid())
                .transactionRef(a.getTransactionRef())
                .userUuid(a.getUserUuid())
                .riskScore(a.getRiskScore())
                .alertType(a.getAlertType())
                .decision(a.getDecision())
                .description(a.getDescription())
                .resolved(a.isResolved())
                .createdAt(a.getCreatedAt())
                .resolvedAt(a.getResolvedAt())
                .build();
    }

    // Converts the score breakdown map to a JSON string for DB storage.
    // If serialization fails, store a fallback string — never fail the evaluation.
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize score breakdown to JSON", e);
            return "{\"error\":\"serialization_failed\"}";
        }
    }
}