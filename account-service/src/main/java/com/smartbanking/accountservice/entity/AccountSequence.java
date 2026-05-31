package com.smartbanking.accountservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// This entity represents a single-row table: account_sequence.
// It stores one number — the current highest account sequence value.
// AccountNumberGenerator reads this, increments it, saves it, and uses
// the new value to format the next account number.
//
// WHY a database row instead of Java AtomicLong?
// AtomicLong resets to 0 when the service restarts.
// If 100 accounts exist and service restarts, next generated number
// would be SBP0000000001 again — COLLISION. DB persistence survives restarts.
//
// WHY a separate table instead of MAX(id) on accounts table?
// MAX(id) under high concurrency with transactions can return stale values.
// A dedicated sequence row with SELECT FOR UPDATE (pessimistic lock)
// guarantees exactly one thread gets each number.

@Entity
@Table(name = "account_sequence")
@Getter
@Setter
@NoArgsConstructor
public class AccountSequence {

    // Always id=1. Single row in the table. We never insert a second row.
    @Id
    private Long id;

    // The current value. Starts at 0 when table is first created.
    // AccountNumberGenerator increments this to get the next account number.
    @Column(nullable = false)
    private Long currentValue;

    public AccountSequence(Long id, Long currentValue) {
        this.id = id;
        this.currentValue = currentValue;
    }
}