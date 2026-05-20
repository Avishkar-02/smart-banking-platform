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
public class AccountCreditedEvent {

    private String transactionRef;
    private String accountNumber;
    private BigDecimal amountCredited;
    private BigDecimal balanceAfterCredit;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}