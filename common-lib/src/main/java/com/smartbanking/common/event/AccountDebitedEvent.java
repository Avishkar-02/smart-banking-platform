package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDebitedEvent {

    private String transactionRef;
    private String accountNumber;
    private BigDecimal amountDebited;

    private BigDecimal balanceAfterDebit;

    private String correlationId;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}