package com.smartbanking.accountservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


@Entity
@Table(name = "accounts",
        // Unique constraints at the database level.
        // DB enforces these even if our Java code has bugs.
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_account_number",
                        columnNames = "account_number"),
                @UniqueConstraint(name = "uk_account_uuid",
                        columnNames = "uuid")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The public UUID for this account — used in inter-service communication.
    // We never expose the internal Long id.
    @Column(nullable = false)
    private String uuid;

    @Column(name = "account_number", nullable = false)
    private String accountNumber;

    // The UUID of the user who owns this account.
    // We do NOT store a foreign key to user-service's database —
    // they are completely separate databases.
    // We store the UUID and trust it to be valid.
    // This is the microservice "database per service" principle.
    @Column(name = "user_uuid", nullable = false)
    private String userUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(nullable = false)
    private String currency;

    // double/float cannot represent 0.1 exactly in binary floating point.
    // 0.1 + 0.2 = 0.30000000000000004 in double.
    // That is a catastrophic bug in a banking application.
    // BigDecimal is exact every time.
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    // ACTIVE, FROZEN, CLOSED.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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

    public enum AccountType {
        SAVINGS,
        CURRENT,
        FIXED_DEPOSIT
    }

    public enum AccountStatus {
        ACTIVE,    // Normal — can transact
        FROZEN,    // Fraud alert — cannot transact but not closed
        CLOSED     // Permanently closed
    }
}