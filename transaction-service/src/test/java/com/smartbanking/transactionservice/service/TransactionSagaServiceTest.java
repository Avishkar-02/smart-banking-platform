package com.smartbanking.transactionservice.service;

import com.smartbanking.common.event.*;
import com.smartbanking.transactionservice.entity.Transaction;
import com.smartbanking.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionSagaService Unit Tests")
class TransactionSagaServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private IdempotencyService idempotencyService;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TransactionSagaService sagaService;

    private static final String TXN_REF  = "TXN-20250519-abc123";
    private static final String SRC_ACC  = "SBP0000000001";
    private static final String DEST_ACC = "SBP0000000002";

    private Transaction processingTransaction;

    @BeforeEach
    void setUp() {
        processingTransaction = Transaction.builder()
                .id(1L).uuid("txn-uuid-456")
                .transactionRef(TXN_REF)
                .idempotencyKey("idem-key-123")
                .sourceAccountNumber(SRC_ACC)
                .destinationAccountNumber(DEST_ACC)
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .status(Transaction.TransactionStatus.FRAUD_CHECKING)
                .initiatedByUuid("user-uuid-123")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ── FRAUD APPROVED ────────────────────────────────────────────────────

    @Test
    @DisplayName("Fraud APPROVED should transition to PROCESSING and publish DebitCommand")
    void shouldAdvanceSagaWhenFraudApproved() {
        FraudResultEvent event = FraudResultEvent.builder()
                .transactionRef(TXN_REF)
                .decision("APPROVED")
                .riskScore(20)
                .build();

        when(transactionRepository.findByTransactionRef(TXN_REF))
                .thenReturn(Optional.of(processingTransaction));
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(processingTransaction);

        sagaService.handleFraudResult(event);

        // Verify status updated to PROCESSING
        ArgumentCaptor<Transaction> txnCaptor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getStatus())
                .isEqualTo(Transaction.TransactionStatus.PROCESSING);

        // Verify DebitCommand published
        verify(kafkaTemplate).send(
                eq("transaction-service.debit.command"),
                eq(TXN_REF),
                any(DebitCommandEvent.class));
    }

    // ── FRAUD BLOCKED ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Fraud BLOCKED should set FAILED status and publish CompletedEvent")
    void shouldFailTransactionWhenFraudBlocked() {
        FraudResultEvent event = FraudResultEvent.builder()
                .transactionRef(TXN_REF)
                .decision("BLOCKED")
                .riskScore(85)
                .reason("High velocity — 6 transactions in 60 seconds")
                .build();

        when(transactionRepository.findByTransactionRef(TXN_REF))
                .thenReturn(Optional.of(processingTransaction));
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(processingTransaction);

        sagaService.handleFraudResult(event);

        ArgumentCaptor<Transaction> txnCaptor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txnCaptor.capture());

        assertThat(txnCaptor.getValue().getStatus())
                .isEqualTo(Transaction.TransactionStatus.FAILED);
        assertThat(txnCaptor.getValue().getFailureReason())
                .contains("High velocity");

        // CompletedEvent(FAILED) published to notification-service
        verify(kafkaTemplate).send(
                eq("transaction-service.transaction.completed"),
                eq(TXN_REF),
                any(TransactionCompletedEvent.class));

        // DebitCommand should NOT be published
        verify(kafkaTemplate, never()).send(
                eq("transaction-service.debit.command"),
                anyString(), any());
    }

    // ── DEBIT CONFIRMED → CREDIT COMMAND ─────────────────────────────────

    @Test
    @DisplayName("AccountDebited should publish CreditCommand to destination account")
    void shouldPublishCreditCommandAfterDebit() {
        AccountDebitedEvent event = AccountDebitedEvent.builder()
                .transactionRef(TXN_REF)
                .accountNumber(SRC_ACC)
                .amountDebited(new BigDecimal("5000.00"))
                .balanceAfterDebit(new BigDecimal("10000.00"))
                .build();

        when(transactionRepository.findByTransactionRef(TXN_REF))
                .thenReturn(Optional.of(processingTransaction));

        sagaService.handleAccountDebited(event);

        // CreditCommand published to destination account
        ArgumentCaptor<CreditCommandEvent> creditCaptor =
                ArgumentCaptor.forClass(CreditCommandEvent.class);
        verify(kafkaTemplate).send(
                eq("transaction-service.credit.command"),
                eq(TXN_REF),
                creditCaptor.capture());

        assertThat(creditCaptor.getValue().getAccountNumber())
                .isEqualTo(DEST_ACC);
        assertThat(creditCaptor.getValue().getAmount())
                .isEqualByComparingTo(new BigDecimal("5000.00"));
    }

    // ── CREDIT CONFIRMED → COMPLETED ─────────────────────────────────────

    @Test
    @DisplayName("AccountCredited should mark COMPLETED and publish CompletedEvent")
    void shouldCompleteTransactionOnCreditConfirmed() {
        AccountCreditedEvent event = AccountCreditedEvent.builder()
                .transactionRef(TXN_REF)
                .accountNumber(DEST_ACC)
                .amountCredited(new BigDecimal("5000.00"))
                .balanceAfterCredit(new BigDecimal("15000.00"))
                .build();

        when(transactionRepository.findByTransactionRef(TXN_REF))
                .thenReturn(Optional.of(processingTransaction));
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(processingTransaction);

        sagaService.handleAccountCredited(event);

        ArgumentCaptor<Transaction> txnCaptor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txnCaptor.capture());

        assertThat(txnCaptor.getValue().getStatus())
                .isEqualTo(Transaction.TransactionStatus.COMPLETED);
        assertThat(txnCaptor.getValue().getCompletedAt()).isNotNull();

        // Success notification published
        verify(kafkaTemplate).send(
                eq("transaction-service.transaction.completed"),
                eq(TXN_REF),
                any(TransactionCompletedEvent.class));
    }

    // ── CREDIT FAILED → REVERSAL ──────────────────────────────────────────

    @Test
    @DisplayName("Credit failure should initiate reversal with REVERSING status")
    void shouldInitiateReversalOnCreditFailure() {
        when(transactionRepository.findByTransactionRef(TXN_REF))
                .thenReturn(Optional.of(processingTransaction));
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(processingTransaction);

        sagaService.handleCreditFailed(TXN_REF, "ACCOUNT_FROZEN");

        ArgumentCaptor<Transaction> txnCaptor =
                ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txnCaptor.capture());
        assertThat(txnCaptor.getValue().getStatus())
                .isEqualTo(Transaction.TransactionStatus.REVERSING);

        // Reversal command published back to account-service
        verify(kafkaTemplate).send(
                eq("transaction-service.reverse.debit.command"),
                eq(TXN_REF),
                any(ReverseDebitCommandEvent.class));
    }
}