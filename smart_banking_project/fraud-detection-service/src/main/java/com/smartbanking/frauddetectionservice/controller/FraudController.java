package com.smartbanking.frauddetectionservice.controller;

import com.smartbanking.common.dto.ApiResponse;
import com.smartbanking.frauddetectionservice.dto.FraudAlertResponse;
import com.smartbanking.frauddetectionservice.dto.FraudEvaluationResponse;
import com.smartbanking.frauddetectionservice.service.FraudDetectionService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Read-only admin endpoints.
// All writes happen through Kafka — no POST/PUT/DELETE here.
// In production, these endpoints would be secured to ADMIN role only
// via the API Gateway routing rules.

@Slf4j
@RestController
@RequestMapping("/api/fraud")
@RequiredArgsConstructor
@Validated
public class FraudController {

    private final FraudDetectionService fraudDetectionService;

    // GET /api/fraud/evaluations/{transactionRef}
    // Returns the full fraud evaluation for a specific transaction.
    // Use this to understand why a transaction was blocked or flagged.
    @GetMapping("/evaluations/{transactionRef}")
    public ResponseEntity<ApiResponse<FraudEvaluationResponse>> getEvaluation(
            @PathVariable @NotBlank String transactionRef) {

        log.debug("Get evaluation request for ref: {}", transactionRef);
        FraudEvaluationResponse response =
                fraudDetectionService.getEvaluationByTransactionRef(transactionRef);

        return ResponseEntity.ok(
                ApiResponse.success(response,
                        "Fraud evaluation fetched successfully"));
    }

    // GET /api/fraud/alerts/user/{userUuid}
    // Returns all fraud alerts for a specific user.
    // Used by compliance team to review a user's risk history.
    @GetMapping("/alerts/user/{userUuid}")
    public ResponseEntity<ApiResponse<List<FraudAlertResponse>>> getAlertsByUser(
            @PathVariable @NotBlank String userUuid) {

        log.debug("Get alerts request for user: {}", userUuid);
        List<FraudAlertResponse> alerts =
                fraudDetectionService.getAlertsByUser(userUuid);

        return ResponseEntity.ok(
                ApiResponse.success(alerts,
                        "Fraud alerts fetched. Count: " + alerts.size()));
    }

    // GET /api/fraud/alerts/recent
    // Returns the 50 most recent unresolved fraud alerts.
    // The admin dashboard "work queue" — what needs review right now.
    @GetMapping("/alerts/recent")
    public ResponseEntity<ApiResponse<List<FraudAlertResponse>>> getRecentAlerts() {

        log.debug("Get recent unresolved alerts request");
        List<FraudAlertResponse> alerts =
                fraudDetectionService.getRecentAlerts();

        return ResponseEntity.ok(
                ApiResponse.success(alerts,
                        "Recent fraud alerts fetched. Count: " + alerts.size()));
    }
}