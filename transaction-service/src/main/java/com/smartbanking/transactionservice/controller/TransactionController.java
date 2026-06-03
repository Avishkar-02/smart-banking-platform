package com.smartbanking.transactionservice.controller;

import com.smartbanking.common.dto.ApiResponse;
import com.smartbanking.transactionservice.dto.TransactionResponse;
import com.smartbanking.transactionservice.dto.TransactionStatusResponse;
import com.smartbanking.transactionservice.dto.TransferRequest;
import com.smartbanking.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @Validated enables @NotBlank on @RequestHeader parameters.
// Without class-level @Validated, header constraints are silently ignored.

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Validated
public class TransactionController {

    private final TransactionService transactionService;

    // POST /api/transactions/transfer
    // Returns 202 Accepted — not 200 or 201.
    // Transfer is async. 202 means "received, processing".
    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("X-User-Uuid")
            @NotBlank(message = "X-User-Uuid header is required") String userUuid,
            @RequestHeader("X-Idempotency-Key")
            @NotBlank(message = "X-Idempotency-Key header is required")
            String idempotencyKey) {

        log.info("Transfer request — from: {}, amount: {}, idempotencyKey: {}",
                request.getSourceAccountNumber(), request.getAmount(),
                idempotencyKey);

        TransactionResponse response = transactionService.initiateTransfer(
                request, userUuid, idempotencyKey);

        // 202 Accepted — processing started but not complete
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response,
                        "Transfer initiated successfully"));
    }

    // GET /api/transactions/{transactionRef}
    // Full transaction details including current status
    @GetMapping("/{transactionRef}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable String transactionRef,
            @RequestHeader("X-User-Uuid")
            @NotBlank(message = "X-User-Uuid header is required")
            String userUuid) {

        log.debug("Get transaction — ref: {}", transactionRef);
        TransactionResponse response =
                transactionService.getTransaction(transactionRef, userUuid);

        return ResponseEntity.ok(
                ApiResponse.success(response, "Transaction fetched successfully"));
    }

    // GET /api/transactions/status/{transactionRef}
    // Lightweight status polling — served from Redis when possible
    @GetMapping("/status/{transactionRef}")
    public ResponseEntity<ApiResponse<TransactionStatusResponse>> getStatus(
            @PathVariable String transactionRef) {

        log.debug("Status check — ref: {}", transactionRef);
        TransactionStatusResponse status =
                transactionService.getStatus(transactionRef);

        return ResponseEntity.ok(
                ApiResponse.success(status, "Status fetched successfully"));
    }

    // GET /api/transactions/history/{accountNumber}
    // All transactions where this account was source or destination
    @GetMapping("/history/{accountNumber}")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getHistory(
            @PathVariable String accountNumber,
            @RequestHeader("X-User-Uuid")
            @NotBlank(message = "X-User-Uuid header is required")
            String userUuid) {

        log.debug("History request — account: {}", accountNumber);
        List<TransactionResponse> history =
                transactionService.getHistory(accountNumber, userUuid);

        return ResponseEntity.ok(
                ApiResponse.success(history,
                        "History fetched. Count: " + history.size()));
    }
}