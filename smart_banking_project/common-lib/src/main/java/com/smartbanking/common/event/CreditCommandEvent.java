package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Published by transaction-service AFTER debit succeeded.
// account-service consumes this to credit the destination account.
// This is step 2 of the saga after debit confirmation.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditCommandEvent {

    private String transactionRef;
    private String accountNumber;      // Destination account to credit
    private BigDecimal amount;
    private String currency;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}