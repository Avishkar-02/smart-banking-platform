package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Published by fraud-detection-service after scoring a transaction.
// transaction-service's SagaEventListener consumes this to decide
// whether to proceed with debit or reject the transaction.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudResultEvent {

    private String transactionRef;

    // "APPROVED" or "BLOCKED"
    // APPROVED → saga proceeds to debit step
    // BLOCKED → saga marks transaction FAILED immediately
    private String decision;

    // Risk score 0-100 calculated by fraud-detection-service
    private int riskScore;

    // Human-readable reason — stored in transaction.failureReason if BLOCKED
    private String reason;

    // "MANUAL_REVIEW" flag — set when circuit breaker fallback was used
    // Means: we could not check fraud properly, review this transaction
    private boolean requiresManualReview;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}