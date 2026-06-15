package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Published by transaction-service WHEN credit step fails AFTER debit succeeded.
// account-service consumes this to reverse (refund) the debit.
// This is the compensating transaction in the saga.
// Without this, source would be debited but destination never credited —
// money would disappear. This event ensures the money is returned.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReverseDebitCommandEvent {

    private String transactionRef;
    private String accountNumber;      // Source account to credit back
    private BigDecimal amount;
    private String currency;
    private String failureReason;      // Why the saga is being reversed

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}