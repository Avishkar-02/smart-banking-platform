package com.smartbanking.transactionservice.service;

import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.transactionservice.dto.TransactionResponse;
import com.smartbanking.transactionservice.dto.TransactionStatusResponse;
import com.smartbanking.transactionservice.dto.TransferRequest;
import com.smartbanking.transactionservice.entity.Transaction;
import com.smartbanking.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Unit Tests")
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private TransactionSagaService sagaService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TransactionService transactionService;

    private static final String USER_UUID   = "user-uuid-123";
    private static final String IDEM_KEY    = "idempotency-key-abc";
    private static final String SOURCE_ACC  = "SBP0000000001";
    private static final String DEST_ACC    = "SBP0000000002";

    private TransferRequest validRequest;
    private Transaction savedTransaction;
    private TransactionResponse sampleResponse;

    @BeforeEach
    void setUp() {
        validRequest = new TransferRequest();
        validRequest.setSourceAccountNumber(SOURCE_ACC);
        validRequest.setDestinationAccountNumber(DEST_ACC);
        validRequest.setAmount(new BigDecimal("5000.00"));
        validRequest.setCurrency("INR");
        validRequest.setDescription("Rent payment");

        savedTransaction = Transaction.builder()
                .id(1L)
                .uuid("txn-uuid-456")
                .transactionRef("TXN-20250519-abc123")
                .idempotencyKey(IDEM_KEY)
                .sourceAccountNumber(SOURCE_ACC)
                .destinationAccountNumber(DEST_ACC)
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .status(Transaction.TransactionStatus.FRAUD_CHECKING)
                .initiatedByUuid(USER_UUID)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleResponse = TransactionResponse.builder()
                .uuid("txn-uuid-456")
                .transactionRef("TXN-20250519-abc123")
                .sourceAccountNumber(SOURCE_ACC)
                .destinationAccountNumber(DEST_ACC)
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .status("FRAUD_CHECKING")
                .build();
    }

    // ── INITIATE TRANSFER ────────────────────────────────────────────────

    @Test
    @DisplayName("Should initiate transfer and return FRAUD_CHECKING status")
    void shouldInitiateTransferSuccessfully() {
        // No cached response — new request
        when(idempotencyService.getCachedResponse(IDEM_KEY))
                .thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenReturn(savedTransaction);
        when(sagaService.mapToResponse(any(Transaction.class)))
                .thenReturn(sampleResponse);

        TransactionResponse response = transactionService.initiateTransfer(
                validRequest, USER_UUID, IDEM_KEY);

        assertThat(response).isNotNull();
        assertThat(response.getTransactionRef()).isEqualTo("TXN-20250519-abc123");
        assertThat(response.getStatus()).isEqualTo("FRAUD_CHECKING");

        // Verify transaction saved once
        verify(transactionRepository, times(1)).save(any(Transaction.class));
        // Verify Kafka event published
        verify(kafkaTemplate, times(1)).send(
                eq("transaction-service.transaction.initiated"),
                anyString(), any());
        // Verify idempotency cached
        verify(idempotencyService, times(1)).cacheResponse(
                eq(IDEM_KEY), any(TransactionResponse.class));
    }

    @Test
    @DisplayName("Should return cached response for duplicate idempotency key")
    void shouldReturnCachedResponseForDuplicate() {
        // Cache HIT — this is a duplicate request
        when(idempotencyService.getCachedResponse(IDEM_KEY))
                .thenReturn(Optional.of(sampleResponse));

        TransactionResponse response = transactionService.initiateTransfer(
                validRequest, USER_UUID, IDEM_KEY);

        assertThat(response.getTransactionRef()).isEqualTo("TXN-20250519-abc123");

        // DB should NEVER be touched for a duplicate
        verify(transactionRepository, never()).save(any());
        // Kafka should NEVER publish for a duplicate
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw SAME_ACCOUNT_TRANSFER when source equals destination")
    void shouldThrowForSameAccountTransfer() {
        when(idempotencyService.getCachedResponse(IDEM_KEY))
                .thenReturn(Optional.empty());

        validRequest.setDestinationAccountNumber(SOURCE_ACC); // Same as source!

        assertThatThrownBy(() ->
                transactionService.initiateTransfer(validRequest, USER_UUID, IDEM_KEY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("same");

        verify(transactionRepository, never()).save(any());
    }

    // ── GET STATUS ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return status from Redis cache without DB call")
    void shouldReturnStatusFromCache() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("txn:status:TXN-20250519-abc123"))
                .thenReturn("COMPLETED");

        TransactionStatusResponse status = transactionService
                .getStatus("TXN-20250519-abc123");

        assertThat(status.getCurrentStatus()).isEqualTo("COMPLETED");
        assertThat(status.getMessage()).contains("completed successfully");

        // DB never called when Redis has the answer
        verify(transactionRepository, never()).findByTransactionRef(anyString());
    }

    @Test
    @DisplayName("Should fall back to MySQL when Redis cache miss")
    void shouldFallbackToMySQLOnCacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // Cache miss

        when(transactionRepository.findByTransactionRef("TXN-20250519-abc123"))
                .thenReturn(Optional.of(savedTransaction));

        TransactionStatusResponse status = transactionService
                .getStatus("TXN-20250519-abc123");

        assertThat(status.getCurrentStatus()).isEqualTo("FRAUD_CHECKING");
        verify(transactionRepository, times(1)).findByTransactionRef(anyString());
    }

    // ── GET TRANSACTION ──────────────────────────────────────────────────

    @Test
    @DisplayName("Should return transaction for the owner")
    void shouldGetTransactionForOwner() {
        when(transactionRepository.findByTransactionRef("TXN-20250519-abc123"))
                .thenReturn(Optional.of(savedTransaction));
        when(sagaService.mapToResponse(savedTransaction)).thenReturn(sampleResponse);

        TransactionResponse response = transactionService
                .getTransaction("TXN-20250519-abc123", USER_UUID);

        assertThat(response.getTransactionRef()).isEqualTo("TXN-20250519-abc123");
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when different user requests transaction")
    void shouldThrowForUnauthorizedAccess() {
        when(transactionRepository.findByTransactionRef("TXN-20250519-abc123"))
                .thenReturn(Optional.of(savedTransaction));

        assertThatThrownBy(() ->
                transactionService.getTransaction(
                        "TXN-20250519-abc123", "attacker-uuid"))
                .isInstanceOf(BusinessException.class);
    }
}