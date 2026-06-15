package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Published by transaction-service AFTER fraud approved the transaction.
// account-service consumes this to debit the source account.
// This is a COMMAND event — it tells account-service to do something.
// Command events are different from notification events — they expect action.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebitCommandEvent {

    private String transactionRef;
    private String accountNumber;      // Source account to debit
    private BigDecimal amount;
    private String currency;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}