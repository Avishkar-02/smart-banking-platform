package com.smartbanking.transactionservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.common.exception.ErrorCode;
import com.smartbanking.transactionservice.dto.TransactionResponse;
import com.smartbanking.transactionservice.dto.TransactionStatusResponse;
import com.smartbanking.transactionservice.dto.TransferRequest;
import com.smartbanking.transactionservice.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@DisplayName("TransactionController Integration Tests")
class TransactionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private TransactionService transactionService;

    private static final String USER_UUID = "user-uuid-123";
    private static final String IDEM_KEY  = "idempotency-key-abc";
    private static final String TXN_REF   = "TXN-20250519-abc123";

    private TransactionResponse sampleResponse() {
        return TransactionResponse.builder()
                .uuid("txn-uuid-456")
                .transactionRef(TXN_REF)
                .sourceAccountNumber("SBP0000000001")
                .destinationAccountNumber("SBP0000000002")
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .status("FRAUD_CHECKING")
                .build();
    }

    private TransferRequest validRequest() {
        TransferRequest r = new TransferRequest();
        r.setSourceAccountNumber("SBP0000000001");
        r.setDestinationAccountNumber("SBP0000000002");
        r.setAmount(new BigDecimal("5000.00"));
        r.setCurrency("INR");
        return r;
    }

    // ── POST /transfer ───────────────────────────────────────────────────

    @Test
    @DisplayName("POST /transfer — 202 Accepted on valid request")
    void shouldInitiateTransfer202() throws Exception {
        when(transactionService.initiateTransfer(any(), anyString(), anyString()))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Uuid", USER_UUID)
                        .header("X-Idempotency-Key", IDEM_KEY)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transactionRef").value(TXN_REF))
                .andExpect(jsonPath("$.data.status").value("FRAUD_CHECKING"));
    }

    @Test
    @DisplayName("POST /transfer — 400 when X-Idempotency-Key header missing")
    void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Uuid", USER_UUID)
                        // No X-Idempotency-Key header
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transfer — 400 when amount is zero")
    void shouldReturn400WhenAmountIsZero() throws Exception {
        TransferRequest bad = validRequest();
        bad.setAmount(BigDecimal.ZERO); // Invalid — must be >= 0.01

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Uuid", USER_UUID)
                        .header("X-Idempotency-Key", IDEM_KEY)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    @DisplayName("POST /transfer — 400 when account number format invalid")
    void shouldReturn400WhenAccountNumberInvalid() throws Exception {
        TransferRequest bad = validRequest();
        bad.setSourceAccountNumber("INVALID123"); // Wrong format

        mockMvc.perform(post("/api/transactions/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Uuid", USER_UUID)
                        .header("X-Idempotency-Key", IDEM_KEY)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /status/{transactionRef} ──────────────────────────────────────

    @Test
    @DisplayName("GET /status/{ref} — 200 with current status")
    void shouldGetStatus200() throws Exception {
        TransactionStatusResponse statusResp = TransactionStatusResponse.builder()
                .transactionRef(TXN_REF)
                .currentStatus("COMPLETED")
                .message("Transfer completed successfully")
                .build();

        when(transactionService.getStatus(TXN_REF)).thenReturn(statusResp);

        mockMvc.perform(get("/api/transactions/status/" + TXN_REF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.transactionRef").value(TXN_REF));
    }

    // ── GET /{transactionRef} ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /{ref} — 200 with full transaction details")
    void shouldGetTransaction200() throws Exception {
        when(transactionService.getTransaction(anyString(), anyString()))
                .thenReturn(sampleResponse());

        mockMvc.perform(get("/api/transactions/" + TXN_REF)
                        .header("X-User-Uuid", USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transactionRef").value(TXN_REF));
    }

    @Test
    @DisplayName("GET /{ref} — 404 when transaction not found")
    void shouldReturn404WhenNotFound() throws Exception {
        when(transactionService.getTransaction(anyString(), anyString()))
                .thenThrow(new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/transactions/TXN-DOESNOTEXIST")
                        .header("X-User-Uuid", USER_UUID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /{ref} — 403 when different user requests transaction")
    void shouldReturn403ForUnauthorizedAccess() throws Exception {
        when(transactionService.getTransaction(anyString(), anyString()))
                .thenThrow(new BusinessException(
                        ErrorCode.UNAUTHORIZED, HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/transactions/" + TXN_REF)
                        .header("X-User-Uuid", "attacker-uuid"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }
}