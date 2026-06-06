package com.smartbanking.frauddetectionservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// Only created when risk score >= 40 (flagged or blocked).
// This is the actionable alert that a compliance officer reviews.
// Think of FraudEvaluation as the full log and FraudAlert as the work queue.

@Entity
@Table(name = "fraud_alerts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alert_uuid", columnNames = "uuid")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String uuid;

    @Column(name = "transaction_ref", nullable = false)
    private String transactionRef;

    @Column(name = "user_uuid", nullable = false)
    private String userUuid;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    // The primary rule that caused the alert.
    // HIGH_AMOUNT, HIGH_VELOCITY, UNUSUAL_HOUR, RAPID_SAME_DEST, COMBINED
    @Column(name = "alert_type", nullable = false)
    private String alertType;

    // "BLOCKED" (score >= 70) or "FLAGGED" (score 40-69)
    @Column(nullable = false)
    private String decision;

    // Readable explanation: "Transaction of ₹1,50,000 at 03:15 AM flagged for review"
    @Column(length = 500)
    private String description;

    // False = open (waiting for review). True = compliance resolved it.
    // Default false — every new alert starts as unresolved.
    @Column(nullable = false)
    private boolean resolved = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        this.uuid = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }
}