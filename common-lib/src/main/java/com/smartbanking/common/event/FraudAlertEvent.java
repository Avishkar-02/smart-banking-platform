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

    private int riskScore;

    private String alertType;

    private String description;

    private String recommendedAction;

    private String correlationId;

    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}