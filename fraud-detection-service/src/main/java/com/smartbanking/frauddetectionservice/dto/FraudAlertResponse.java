package com.smartbanking.frauddetectionservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FraudAlertResponse {

    private String uuid;
    private String transactionRef;
    private String userUuid;
    private int riskScore;
    private String alertType;
    private String decision;
    private String description;
    private boolean resolved;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}