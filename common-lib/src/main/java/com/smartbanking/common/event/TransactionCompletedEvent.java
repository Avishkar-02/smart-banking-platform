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
public class TransactionCompletedEvent {

    private String transactionRef;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private BigDecimal amount;
    private String currency;
    private String senderEmail;
    private String receiverEmail;

    // "COMPLETED" or "FAILED" — notification-service sends different messages
    private String finalStatus;

    // Only populated when finalStatus = "FAILED"
    private String failureReason;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}