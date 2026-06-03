package com.smartbanking.transactionservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Full transfer details — returned after creation and on GET requests.
// JsonInclude.NON_NULL hides failureReason and completedAt when null
// (i.e. when transfer is still in progress).

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResponse {

    private String uuid;
    private String transactionRef;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String description;

    // Only shown when transfer failed
    private String failureReason;

    // Only shown when requiresManualReview = true (circuit breaker fallback used)
    private Boolean requiresManualReview;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Only shown when terminal state reached
    private LocalDateTime completedAt;
}