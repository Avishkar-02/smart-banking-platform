package com.smartbanking.frauddetectionservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Every transaction that passes through fraud detection gets a record here.
// This is the complete audit trail — compliance teams can see every decision ever made.
// Even APPROVED transactions are recorded — useful for detecting
// false negatives (transactions we approved that were actually fraud).

@Entity
@Table(name = "fraud_evaluations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_evaluation_transaction_ref",
                        columnNames = "transaction_ref"),
                @UniqueConstraint(name = "uk_evaluation_uuid",
                        columnNames = "uuid")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String uuid;

    // Links this evaluation to the transaction in transaction-service.
    // Unique — one evaluation per transaction.
    @Column(name = "transaction_ref", nullable = false)
    private String transactionRef;

    // Who initiated the transaction — used for user-level fraud pattern analysis
    @Column(name = "user_uuid", nullable = false)
    private String userUuid;

    @Column(name = "source_account_number", nullable = false)
    private String sourceAccountNumber;

    @Column(name = "destination_account_number", nullable = false)
    private String destinationAccountNumber;

    // The actual amount — needed to re-evaluate rules if thresholds change
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    // Final risk score 0-100+
    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    // "APPROVED" or "BLOCKED"
    @Column(nullable = false)
    private String decision;

    // JSON string showing which rules fired and how many points each added.
    // Example: {"HIGH_AMOUNT":40,"HIGH_VELOCITY":50,"total":90}
    // Stored as TEXT — not a separate table — for simplicity.
    @Column(name = "score_breakdown", columnDefinition = "TEXT")
    private String scoreBreakdown;

    // True when score 40-69 — compliance team needs to review
    @Column(name = "requires_manual_review", nullable = false)
    private boolean requiresManualReview;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private LocalDateTime evaluatedAt;

    @PrePersist
    protected void onCreate() {
        this.uuid = UUID.randomUUID().toString();
        this.evaluatedAt = LocalDateTime.now();
    }
}