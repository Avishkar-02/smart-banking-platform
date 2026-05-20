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
public class TransactionCreatedEvent {

    private String transactionRef;
    private String sourceAccountNumber;
    private String destinationAccountNumber;

    // BigDecimal — NEVER double or float for money.
    // 0.1 + 0.2 = 0.30000000000000004 in double. That's a banking bug.
    // BigDecimal is exact.
    private BigDecimal amount;

    private String currency;
    private String initiatedByUuid;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}