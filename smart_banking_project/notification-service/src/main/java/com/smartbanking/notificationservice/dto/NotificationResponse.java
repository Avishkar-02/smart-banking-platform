package com.smartbanking.notificationservice.dto;

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
public class NotificationResponse {

    private String uuid;
    private String recipientEmail;
    private String notificationType;
    private String subject;
    private String status;
    // Only shown on failure
    private String failureReason;
    private String transactionRef;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}