package com.smartbanking.frauddetectionservice.controller;

import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.common.exception.ErrorCode;
import com.smartbanking.frauddetectionservice.dto.FraudAlertResponse;
import com.smartbanking.frauddetectionservice.dto.FraudEvaluationResponse;
import com.smartbanking.frauddetectionservice.service.FraudDetectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FraudController.class)
@DisplayName("FraudController Integration Tests")
class FraudControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean  private FraudDetectionService fraudDetectionService;

    private static final String TXN_REF  = "TXN-TEST-001";
    private static final String USER_UUID = "user-uuid-123";

    private FraudEvaluationResponse sampleEvaluation() {
        return FraudEvaluationResponse.builder()
                .uuid("eval-uuid-001")
                .transactionRef(TXN_REF)
                .userUuid(USER_UUID)
                .sourceAccountNumber("SBP0000000001")
                .destinationAccountNumber("SBP0000000002")
                .amount(new BigDecimal("5000.00"))
                .riskScore(10)
                .decision("APPROVED")
                .scoreBreakdown("{\"HIGH_AMOUNT\":0,\"HIGH_VELOCITY\":0}")
                .requiresManualReview(false)
                .evaluatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /evaluations/{ref} — 200 with evaluation details")
    void shouldGetEvaluation200() throws Exception {
        when(fraudDetectionService.getEvaluationByTransactionRef(TXN_REF))
                .thenReturn(sampleEvaluation());

        mockMvc.perform(get("/api/fraud/evaluations/" + TXN_REF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transactionRef").value(TXN_REF))
                .andExpect(jsonPath("$.data.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.riskScore").value(10));
    }

    @Test
    @DisplayName("GET /evaluations/{ref} — 404 when evaluation not found")
    void shouldReturn404WhenNotFound() throws Exception {
        when(fraudDetectionService.getEvaluationByTransactionRef(anyString()))
                .thenThrow(new BusinessException(
                        ErrorCode.TRANSACTION_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Fraud evaluation not found"));

        mockMvc.perform(get("/api/fraud/evaluations/TXN-NOT-EXISTS"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /alerts/user/{userUuid} — 200 with list of alerts")
    void shouldGetAlertsByUser200() throws Exception {
        FraudAlertResponse alert = FraudAlertResponse.builder()
                .uuid("alert-uuid-001")
                .transactionRef(TXN_REF)
                .userUuid(USER_UUID)
                .riskScore(50)
                .alertType("HIGH_AMOUNT")
                .decision("FLAGGED")
                .description("Transaction of INR 150,000 flagged for review")
                .resolved(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(fraudDetectionService.getAlertsByUser(USER_UUID))
                .thenReturn(List.of(alert));

        mockMvc.perform(get("/api/fraud/alerts/user/" + USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].alertType").value("HIGH_AMOUNT"))
                .andExpect(jsonPath("$.data[0].decision").value("FLAGGED"))
                .andExpect(jsonPath("$.data[0].riskScore").value(50));
    }

    @Test
    @DisplayName("GET /alerts/recent — 200 with list of unresolved alerts")
    void shouldGetRecentAlerts200() throws Exception {
        when(fraudDetectionService.getRecentAlerts()).thenReturn(List.of());

        mockMvc.perform(get("/api/fraud/alerts/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /alerts/user/{userUuid} — 200 with empty list when no alerts")
    void shouldReturn200WithEmptyListWhenNoAlerts() throws Exception {
        when(fraudDetectionService.getAlertsByUser(anyString()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/fraud/alerts/user/" + USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}