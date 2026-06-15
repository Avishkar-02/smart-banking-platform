package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Published by account-service after successfully reversing a debit.
// transaction-service's SagaEventListener marks the transaction REVERSED.
// This closes the compensation loop — money is back where it started.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitReversedEvent {

    private String transactionRef;
    private String accountNumber;      // Account that was refunded
    private BigDecimal amountRefunded;
    private String failureReason;      // Original reason for the reversal

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}