package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Published by account-service after successfully debiting source account.
// transaction-service listens to this to know debit succeeded
// and proceed to the credit step.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDebitedEvent {

    private String transactionRef;
    private String accountNumber;
    private BigDecimal amountDebited;
    private BigDecimal balanceAfterDebit;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}