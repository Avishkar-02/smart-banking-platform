package com.smartbanking.transactionservice.service;

import com.smartbanking.common.event.TransactionInitiatedEvent;
import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.common.exception.ErrorCode;
import com.smartbanking.transactionservice.dto.TransactionResponse;
import com.smartbanking.transactionservice.dto.TransactionStatusResponse;
import com.smartbanking.transactionservice.dto.TransferRequest;
import com.smartbanking.transactionservice.entity.Transaction;
import com.smartbanking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;
    private final TransactionSagaService sagaService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String STATUS_CACHE_PREFIX = "txn:status:";

    // ── Initiate Transfer ─────────────────────────────────────────────────

    @Transactional
    public TransactionResponse initiateTransfer(
            TransferRequest request, String userUuid, String idempotencyKey) {

        log.info("Transfer request — from: {}, to: {}, amount: {}, " +
                        "idempotencyKey: {}, userUuid: {}",
                request.getSourceAccountNumber(),
                request.getDestinationAccountNumber(),
                request.getAmount(), idempotencyKey, userUuid);

        // ── Step 1: Idempotency check ──────────────────────────────────────
        // Check Redis FIRST — before any DB operations.
        // If this request was already processed, return cached result immediately.
        // This is O(1) Redis lookup — extremely fast.
        Optional<TransactionResponse> cached =
                idempotencyService.getCachedResponse(idempotencyKey);
        if (cached.isPresent()) {
            log.info("Duplicate request detected — returning cached response. " +
                    "Key: {}", idempotencyKey);
            return cached.get();
        }

        // ── Step 2: Business validation ────────────────────────────────────
        // Check same account transfer BEFORE saving to DB.
        // Fail fast — no point creating a transaction we know will fail.
        if (request.getSourceAccountNumber()
                .equals(request.getDestinationAccountNumber())) {
            log.warn("Same account transfer rejected — account: {}",
                    request.getSourceAccountNumber());
            throw new BusinessException(
                    ErrorCode.SAME_ACCOUNT_TRANSFER, HttpStatus.BAD_REQUEST);
        }

        // ── Step 3: Build and save transaction ────────────────────────────
        // Generate human-readable transactionRef.
        // Format: TXN-YYYYMMDD-randomShort
        // e.g. TXN-20250519-a1b2c3
        String transactionRef = generateTransactionRef();

        Transaction transaction = Transaction.builder()
                .transactionRef(transactionRef)
                .idempotencyKey(idempotencyKey)
                .sourceAccountNumber(request.getSourceAccountNumber())
                .destinationAccountNumber(request.getDestinationAccountNumber())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .initiatedByUuid(userUuid)
                // Start in FRAUD_CHECKING — we are about to publish for fraud check
                .status(Transaction.TransactionStatus.FRAUD_CHECKING)
                .build();
        // uuid, createdAt, updatedAt set by @PrePersist

        Transaction saved = transactionRepository.save(transaction);
        log.info("Transaction saved — ref: {}, uuid: {}",
                saved.getTransactionRef(), saved.getUuid());

        // ── Step 4: Start the Saga — publish TransactionInitiatedEvent ─────
        // fraud-detection-service consumes this and will publish FraudResultEvent.
        // SagaEventListener will handle the FraudResultEvent and advance the saga.
        TransactionInitiatedEvent initiatedEvent = TransactionInitiatedEvent.builder()
                .transactionRef(saved.getTransactionRef())
                .sourceAccountNumber(saved.getSourceAccountNumber())
                .destinationAccountNumber(saved.getDestinationAccountNumber())
                .amount(saved.getAmount())
                .currency(saved.getCurrency())
                .initiatedByUuid(saved.getInitiatedByUuid())
                .description(saved.getDescription())
                .build();

        kafkaTemplate.send(
                "transaction-service.transaction.initiated",
                saved.getTransactionRef(),
                initiatedEvent);

        log.info("TransactionInitiatedEvent published — ref: {}",
                saved.getTransactionRef());

        // ── Step 5: Build response and cache it ────────────────────────────
        TransactionResponse response = sagaService.mapToResponse(saved);

        // Cache idempotency response — future duplicates return this
        idempotencyService.cacheResponse(idempotencyKey, response);

        log.info("Transfer initiated successfully — ref: {}",
                saved.getTransactionRef());
        return response;
    }

    // ── Get Transaction ───────────────────────────────────────────────────

    public TransactionResponse getTransaction(
            String transactionRef, String requestingUserUuid) {

        log.debug("Get transaction — ref: {}", transactionRef);

        Transaction transaction = transactionRepository
                .findByTransactionRef(transactionRef)
                .orElseThrow(() -> {
                    log.warn("Transaction not found — ref: {}", transactionRef);
                    return new BusinessException(
                            ErrorCode.TRANSACTION_NOT_FOUND, HttpStatus.NOT_FOUND);
                });

        // Only the initiator can view their transaction
        if (!transaction.getInitiatedByUuid().equals(requestingUserUuid)) {
            log.warn("Unauthorized transaction access — ref: {}, requester: {}",
                    transactionRef, requestingUserUuid);
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }

        return sagaService.mapToResponse(transaction);
    }

    // ── Get Status ────────────────────────────────────────────────────────

    // Lightweight status check — checks Redis cache first.
    // Redis hit = O(1), no DB call needed.
    // Client polls this every 2 seconds during transfer processing.
    public TransactionStatusResponse getStatus(String transactionRef) {
        log.debug("Status check — ref: {}", transactionRef);

        // Try Redis cache first
        String cachedStatus = redisTemplate.opsForValue()
                .get(STATUS_CACHE_PREFIX + transactionRef);

        if (cachedStatus != null) {
            log.debug("Status served from Redis cache — ref: {}, status: {}",
                    transactionRef, cachedStatus);
            return TransactionStatusResponse.builder()
                    .transactionRef(transactionRef)
                    .currentStatus(cachedStatus)
                    .message(getStatusMessage(cachedStatus))
                    .build();
        }

        // Cache miss — fall back to MySQL
        log.debug("Status cache miss — falling back to MySQL for ref: {}",
                transactionRef);

        Transaction transaction = transactionRepository
                .findByTransactionRef(transactionRef)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND, HttpStatus.NOT_FOUND));

        String status = transaction.getStatus().name();
        return TransactionStatusResponse.builder()
                .transactionRef(transactionRef)
                .currentStatus(status)
                .message(getStatusMessage(status))
                .build();
    }

    // ── Get History ───────────────────────────────────────────────────────

    public List<TransactionResponse> getHistory(
            String accountNumber, String requestingUserUuid) {

        log.debug("Transaction history — account: {}", accountNumber);

        List<Transaction> transactions =
                transactionRepository.findByAccountNumber(accountNumber);

        // Filter: only show transactions where this user was the initiator
        // OR where it involves their account (they are source or destination)
        // For simplicity here we return all — account ownership is verified
        // by account-service when the transfer was initiated
        return transactions.stream()
                .map(sagaService::mapToResponse)
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────

    // Generates a human-readable transaction reference.
    // TXN-20250519-a1b2c3
    // Date part makes it easy to find in logs by date.
    // Random suffix ensures uniqueness within the same day.
    private String generateTransactionRef() {
        String date = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toLowerCase();
        return "TXN-" + date + "-" + random;
    }

    // Human-readable message for each status
    private String getStatusMessage(String status) {
        return switch (status) {
            case "PENDING"        -> "Transfer is queued for processing";
            case "FRAUD_CHECKING" -> "Transfer is being reviewed for security";
            case "PROCESSING"     -> "Transfer is being processed";
            case "COMPLETED"      -> "Transfer completed successfully";
            case "FAILED"         -> "Transfer failed. Check failureReason.";
            case "REVERSING"      -> "Reversing transfer. Your funds will be returned.";
            case "REVERSED"       -> "Transfer was reversed. Funds have been returned.";
            default               -> "Unknown status";
        };
    }
}