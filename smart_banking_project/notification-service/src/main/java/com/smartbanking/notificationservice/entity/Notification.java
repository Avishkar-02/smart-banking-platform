package com.smartbanking.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// Every email attempt is stored here regardless of success or failure.
// This is the audit trail — compliance needs to prove that fraud alerts
// were sent, that users were notified of failed transfers, etc.
// If email sending fails, we store FAILED status + reason instead of silently dropping.

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String uuid;

    // Who received this email
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    // UUID of the user who received this — for querying all notifications for a user
    @Column(name = "recipient_uuid")
    private String recipientUuid;

    // The type of notification — used to prevent duplicate sends
    // and for filtering in the admin endpoint
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    @Column(nullable = false)
    private String subject;

    // Full email body stored for audit — you can see exactly what was sent
    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    // Links to the transaction this notification is about.
    // NULL for welcome emails — no transaction involved.
    @Column(name = "transaction_ref")
    private String transactionRef;

    // SENT or FAILED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    // Only populated when status = FAILED — the exception message
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Null until email actually sends successfully
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        this.uuid = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    public enum NotificationType {
        WELCOME,             // New user registered
        TRANSFER_SUCCESS,    // Transfer completed successfully
        TRANSFER_FAILED,     // Transfer failed or was blocked
        FRAUD_ALERT          // Suspicious activity detected
    }

    public enum NotificationStatus {
        SENT,    // Email delivered to SMTP server successfully
        FAILED   // Email delivery failed — see failureReason
    }
}