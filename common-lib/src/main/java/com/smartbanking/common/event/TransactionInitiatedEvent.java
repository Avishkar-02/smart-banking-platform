package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Published by transaction-service immediately after saving the transaction.
// fraud-detection-service consumes this to score the risk.
// This is the first event in the saga chain.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionInitiatedEvent {

    // The human-readable reference — TXN-20250519-abc123
    // All saga steps refer to this reference to coordinate
    private String transactionRef;

    private String sourceAccountNumber;
    private String destinationAccountNumber;

    // BigDecimal — never double for money
    private BigDecimal amount;
    private String currency;

    // UUID of the user who initiated the transfer
    // fraud-detection uses this to check user's transaction history
    private String initiatedByUuid;

    private String description;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}