package com.smartbanking.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertEvent {

    private String transactionRef;
    private String userUuid;
    private String userEmail;

    // 0-100. Higher = more suspicious.
    private int riskScore;

    // "HIGH_AMOUNT", "HIGH_FREQUENCY", "UNUSUAL_PATTERN"
    private String alertType;

    private String description;

    // "FLAG" = mark for review, "BLOCK" = freeze account
    private String recommendedAction;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}