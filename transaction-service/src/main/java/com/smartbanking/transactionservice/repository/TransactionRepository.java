package com.smartbanking.transactionservice.repository;

import com.smartbanking.transactionservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Used to look up transaction during saga steps.
    // Every Kafka event carries transactionRef so we can find and update
    // the right transaction when AccountDebitedEvent arrives.
    Optional<Transaction> findByTransactionRef(String transactionRef);

    // Used by IdempotencyService to check if a key was already processed.
    // Returns the existing transaction so we can return the cached response.
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    // Used to check duplicate idempotency key before saving.
    boolean existsByIdempotencyKey(String idempotencyKey);

    // Returns all transactions where this account was sender OR receiver.
    // Custom @Query needed because Spring Data cannot auto-generate OR queries
    // from a single method name.
    //Give me all Transaction objects where the account number is either the sender account or the receiver account, sorted by newest transaction first.

    @Query("SELECT t FROM Transaction t WHERE " +
            "t.sourceAccountNumber = :accountNumber OR " +
            "t.destinationAccountNumber = :accountNumber " +
            "ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountNumber(
            @Param("accountNumber") String accountNumber);

    // Get all transactions initiated by a specific user — for admin audit
    List<Transaction> findByInitiatedByUuidOrderByCreatedAtDesc(
            String initiatedByUuid);
}