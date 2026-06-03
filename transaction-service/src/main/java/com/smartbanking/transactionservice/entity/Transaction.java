package com.smartbanking.transactionservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Maps to transaction_db.transactions table.
// transaction-service is the ONLY service that writes to this table.
// It owns the full lifecycle of a transfer from initiation to completion.

@Entity
@Table(name = "transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_transaction_ref",
                        columnNames = "transaction_ref"),
                @UniqueConstraint(name = "uk_idempotency_key",
                        columnNames = "idempotency_key"),
                @UniqueConstraint(name = "uk_transaction_uuid",
                        columnNames = "uuid")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public UUID — used in Kafka events and API responses
    @Column(nullable = false)
    private String uuid;

    // Human-readable reference — what users see in statements and support tickets.
    // Format: TXN-YYYYMMDD-randomShort e.g. TXN-20250519-a1b2c3
    // This is used as the Kafka event key — all saga events carry this reference.
    @Column(name = "transaction_ref", nullable = false)
    private String transactionRef;

    // Client-provided idempotency key.
    // Unique constraint means the same key can never create two transactions.
    // nullable = true because we reject requests without this key before saving,
    // but the column allows null as defensive DB design.
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "source_account_number", nullable = false)
    private String sourceAccountNumber;

    @Column(name = "destination_account_number", nullable = false)
    private String destinationAccountNumber;

    // DECIMAL(19,4) — exact precision for monetary amounts
    // Never double or float. BigDecimal is exact.
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    // The saga state machine — see TransactionStatus enum below
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    // Populated when status = FAILED or REVERSED
    // Shows why the transfer did not complete
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    // UUID of the user who initiated the transfer
    // Stored here for audit trail and fraud pattern analysis
    @Column(name = "initiated_by_uuid", nullable = false)
    private String initiatedByUuid;

    // Optional note from the sender e.g. "Rent payment for May"
    @Column(length = 500)
    private String description;

    // When circuit breaker fallback was used — compliance team reviews these
    @Column(name = "requires_manual_review", nullable = false)
    private boolean requiresManualReview = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Set when saga reaches terminal state: COMPLETED, FAILED, or REVERSED
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        this.uuid = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // The complete state machine for a transfer saga
    public enum TransactionStatus {
        PENDING,           // Saved to DB. Saga not started yet.
        FRAUD_CHECKING,    // TransactionInitiatedEvent published. Awaiting fraud result.
        PROCESSING,        // Fraud approved. DebitCommand sent. Awaiting debit confirmation.
        COMPLETED,         // Both debit and credit succeeded. Terminal success state.
        FAILED,            // Any step failed. No money moved or reversal done. Terminal failure.
        REVERSING,         // Debit succeeded but credit failed. Reversal in progress.
        REVERSED           // Debit was reversed. Money back in source. Terminal failure.
    }
}