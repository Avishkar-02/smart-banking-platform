package com.smartbanking.transactionservice.service;

import com.smartbanking.common.event.*;
import com.smartbanking.transactionservice.dto.TransactionResponse;
import com.smartbanking.transactionservice.entity.Transaction;
import com.smartbanking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

// TransactionSagaService is the Saga state machine.
// It receives saga events from SagaEventListener and advances the saga
// to the next state, publishing the appropriate next command.
//
// This class handles ALL state transitions.
// Never update transaction status anywhere else — all state changes go here.

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSagaService {

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final IdempotencyService idempotencyService;
    private final RedisTemplate<String, String> redisTemplate;

    // Redis key prefix for hot status cache
    private static final String STATUS_CACHE_PREFIX = "txn:status:";
    private static final long STATUS_CACHE_TTL_MINUTES = 60;

    // ── Saga Step 1: Fraud result received ───────────────────────────────

    // Called when fraud-detection-service publishes its decision.
    // APPROVED → advance to PROCESSING, publish DebitCommand
    // BLOCKED → terminate saga, mark FAILED

    @Transactional
    public void handleFraudResult(FraudResultEvent event) {
        log.info("Fraud result received — ref: {}, decision: {}, score: {}",
                event.getTransactionRef(), event.getDecision(),
                event.getRiskScore());

        Transaction transaction = findTransaction(event.getTransactionRef());

        if (!"APPROVED".equals(event.getDecision())) {
            // Fraud blocked this transaction
            log.warn("Transaction BLOCKED by fraud detection — ref: {}, reason: {}",
                    event.getTransactionRef(), event.getReason());

            failTransaction(transaction, event.getReason() != null
                    ? event.getReason() : "Transaction blocked by fraud detection");
            return;
        }

        // Fraud approved — check if manual review is required (circuit breaker fallback)
        if (event.isRequiresManualReview()) {
            transaction.setRequiresManualReview(true);
            log.warn("Transaction flagged for manual review — ref: {}",
                    event.getTransactionRef());
        }

        // Advance saga: FRAUD_CHECKING → PROCESSING
        transaction.setStatus(Transaction.TransactionStatus.PROCESSING);
        transactionRepository.save(transaction);
        updateStatusCache(transaction.getTransactionRef(), "PROCESSING");

        // Publish DebitCommand — account-service will debit the source account
        DebitCommandEvent debitCommand = DebitCommandEvent.builder()
                .transactionRef(transaction.getTransactionRef())
                .accountNumber(transaction.getSourceAccountNumber())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .build();

        kafkaTemplate.send(
                "transaction-service.debit.command",
                transaction.getTransactionRef(),
                debitCommand);

        log.info("DebitCommand published — ref: {}, account: {}, amount: {}",
                transaction.getTransactionRef(),
                transaction.getSourceAccountNumber(),
                transaction.getAmount());
    }

    // ── Saga Step 2: Debit confirmed ──────────────────────────────────────

    // Called when account-service successfully debits the source account.
    // Now publish CreditCommand to credit the destination.

    @Transactional
    public void handleAccountDebited(AccountDebitedEvent event) {
        log.info("Account debited confirmed — ref: {}, account: {}, amount: {}",
                event.getTransactionRef(), event.getAccountNumber(),
                event.getAmountDebited());

        Transaction transaction = findTransaction(event.getTransactionRef());

        // Publish CreditCommand — account-service will credit the destination
        CreditCommandEvent creditCommand = CreditCommandEvent.builder()
                .transactionRef(transaction.getTransactionRef())
                .accountNumber(transaction.getDestinationAccountNumber())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .build();

        kafkaTemplate.send(
                "transaction-service.credit.command",
                transaction.getTransactionRef(),
                creditCommand);

        log.info("CreditCommand published — ref: {}, account: {}, amount: {}",
                transaction.getTransactionRef(),
                transaction.getDestinationAccountNumber(),
                transaction.getAmount());
    }

    // ── Saga Step 3: Credit confirmed ─────────────────────────────────────

    // Called when account-service successfully credits the destination.
    // Saga is complete. Mark COMPLETED and publish TransactionCompletedEvent.

    @Transactional
    public void handleAccountCredited(AccountCreditedEvent event) {
        log.info("Account credited confirmed — ref: {}, account: {}, amount: {}",
                event.getTransactionRef(), event.getAccountNumber(),
                event.getAmountCredited());

        Transaction transaction = findTransaction(event.getTransactionRef());

        // Mark terminal success state
        transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(transaction);

        updateStatusCache(saved.getTransactionRef(), "COMPLETED");

        // Update idempotency cache with final COMPLETED status
        if (saved.getIdempotencyKey() != null) {
            idempotencyService.updateCachedResponse(
                    saved.getIdempotencyKey(), mapToResponse(saved));
        }

        // Notify notification-service to send success emails/SMS
        TransactionCompletedEvent completedEvent = TransactionCompletedEvent.builder()
                .transactionRef(saved.getTransactionRef())
                .sourceAccountNumber(saved.getSourceAccountNumber())
                .destinationAccountNumber(saved.getDestinationAccountNumber())
                .amount(saved.getAmount())
                .currency(saved.getCurrency())
                .finalStatus("COMPLETED")
                .build();

        kafkaTemplate.send(
                "transaction-service.transaction.completed",
                saved.getTransactionRef(),
                completedEvent);

        log.info("Transaction COMPLETED successfully — ref: {}",
                saved.getTransactionRef());
    }

    // ── Saga Compensation: Debit failed ───────────────────────────────────

    // Called when account-service rejects the debit (insufficient funds, frozen etc.)
    // No money has moved. Simply mark the transaction FAILED.
    // No reversal needed because debit never happened.

    @Transactional
    public void handleDebitFailed(String transactionRef, String reason) {
        log.warn("Debit FAILED — ref: {}, reason: {}", transactionRef, reason);
        Transaction transaction = findTransaction(transactionRef);
        failTransaction(transaction, reason);
    }

    // ── Saga Compensation: Credit failed ─────────────────────────────────

    // Called when account-service rejects the credit AFTER debit already happened.
    // This is the critical scenario — we must reverse the debit.
    // REVERSING state means: reversal in progress.

    @Transactional
    public void handleCreditFailed(String transactionRef, String reason) {
        log.error("Credit FAILED after successful debit — ref: {}, reason: {}. " +
                "Initiating reversal.", transactionRef, reason);

        Transaction transaction = findTransaction(transactionRef);

        // Mark as reversing — not yet reversed
        transaction.setStatus(Transaction.TransactionStatus.REVERSING);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);
        updateStatusCache(transactionRef, "REVERSING");

        // Publish ReverseDebitCommand — account-service will refund the source
        ReverseDebitCommandEvent reverseCommand = ReverseDebitCommandEvent.builder()
                .transactionRef(transaction.getTransactionRef())
                .accountNumber(transaction.getSourceAccountNumber())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .failureReason(reason)
                .build();

        kafkaTemplate.send(
                "transaction-service.reverse.debit.command",
                transactionRef,
                reverseCommand);

        log.info("ReverseDebitCommand published — ref: {}, account: {}",
                transactionRef, transaction.getSourceAccountNumber());
    }

    // ── Saga Compensation: Debit reversed ────────────────────────────────

    // Called when account-service confirms the reversal.
    // Money is back in source account. Mark REVERSED (terminal failure).

    @Transactional
    public void handleDebitReversed(DebitReversedEvent event) {
        log.info("Debit REVERSED successfully — ref: {}, account: {}, amount: {}",
                event.getTransactionRef(), event.getAccountNumber(),
                event.getAmountRefunded());

        Transaction transaction = findTransaction(event.getTransactionRef());

        transaction.setStatus(Transaction.TransactionStatus.REVERSED);
        transaction.setCompletedAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(transaction);

        updateStatusCache(saved.getTransactionRef(), "REVERSED");

        if (saved.getIdempotencyKey() != null) {
            idempotencyService.updateCachedResponse(
                    saved.getIdempotencyKey(), mapToResponse(saved));
        }

        // Notify: transfer failed but money was refunded
        TransactionCompletedEvent failedEvent = TransactionCompletedEvent.builder()
                .transactionRef(saved.getTransactionRef())
                .sourceAccountNumber(saved.getSourceAccountNumber())
                .destinationAccountNumber(saved.getDestinationAccountNumber())
                .amount(saved.getAmount())
                .currency(saved.getCurrency())
                .finalStatus("FAILED")
                .failureReason(saved.getFailureReason())
                .build();

        kafkaTemplate.send(
                "transaction-service.transaction.completed",
                saved.getTransactionRef(),
                failedEvent);

        log.info("Transaction REVERSED — ref: {}. Source account refunded.",
                saved.getTransactionRef());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    // Central method to find a transaction by ref or throw.
    // All saga methods use this — one place to handle the "not found" error.
    private Transaction findTransaction(String transactionRef) {
        return transactionRepository.findByTransactionRef(transactionRef)
                .orElseThrow(() -> {
                    log.error("CRITICAL: transaction not found for ref: {}. " +
                            "Possible data inconsistency.", transactionRef);
                    return new RuntimeException(
                            "Transaction not found: " + transactionRef);
                });
    }

    // Central method to mark a transaction as FAILED.
    // Used by fraud rejection and debit failure paths.
    private void failTransaction(Transaction transaction, String reason) {
        transaction.setStatus(Transaction.TransactionStatus.FAILED);
        transaction.setFailureReason(reason);
        transaction.setCompletedAt(LocalDateTime.now());
        Transaction saved = transactionRepository.save(transaction);

        updateStatusCache(saved.getTransactionRef(), "FAILED");

        if (saved.getIdempotencyKey() != null) {
            idempotencyService.updateCachedResponse(
                    saved.getIdempotencyKey(), mapToResponse(saved));
        }

        // Notify failure
        TransactionCompletedEvent failedEvent = TransactionCompletedEvent.builder()
                .transactionRef(saved.getTransactionRef())
                .sourceAccountNumber(saved.getSourceAccountNumber())
                .destinationAccountNumber(saved.getDestinationAccountNumber())
                .amount(saved.getAmount())
                .currency(saved.getCurrency())
                .finalStatus("FAILED")
                .failureReason(reason)
                .build();

        kafkaTemplate.send(
                "transaction-service.transaction.completed",
                saved.getTransactionRef(),
                failedEvent);
    }

    // Stores current status in Redis for fast polling without DB hits
    private void updateStatusCache(String transactionRef, String status) {
        redisTemplate.opsForValue().set(
                STATUS_CACHE_PREFIX + transactionRef,
                status,
                STATUS_CACHE_TTL_MINUTES,
                TimeUnit.MINUTES);
    }

    // Maps Transaction entity to TransactionResponse DTO
    public TransactionResponse mapToResponse(Transaction t) {
        return TransactionResponse.builder()
                .uuid(t.getUuid())
                .transactionRef(t.getTransactionRef())
                .sourceAccountNumber(t.getSourceAccountNumber())
                .destinationAccountNumber(t.getDestinationAccountNumber())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .status(t.getStatus().name())
                .description(t.getDescription())
                .failureReason(t.getFailureReason())
                .requiresManualReview(
                        t.isRequiresManualReview() ? true : null)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .completedAt(t.getCompletedAt())
                .build();
    }
}