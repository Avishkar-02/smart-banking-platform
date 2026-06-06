package com.smartbanking.frauddetectionservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// What the admin sees when they look up a fraud evaluation.
// Includes the full score breakdown so they can understand why a
// transaction was flagged or blocked.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FraudEvaluationResponse {

    private String uuid;
    private String transactionRef;
    private String userUuid;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private BigDecimal amount;
    private int riskScore;
    private String decision;
    // The JSON breakdown string — shows which rules fired
    private String scoreBreakdown;
    private boolean requiresManualReview;
    private LocalDateTime evaluatedAt;
}