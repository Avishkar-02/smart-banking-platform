package com.smartbanking.accountservice.controller;

import com.smartbanking.accountservice.dto.AccountResponse;
import com.smartbanking.accountservice.dto.BalanceResponse;
import com.smartbanking.accountservice.dto.CreateAccountRequest;
import com.smartbanking.accountservice.service.AccountService;
import com.smartbanking.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @Validated — required at class level to enable @NotBlank on @RequestHeader parameters.
// Without this, @NotBlank on method parameters (not @RequestBody) is silently ignored.
// @Valid works on @RequestBody, @Validated works on @RequestParam and @RequestHeader.

@Slf4j
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accountService;

    // POST /api/accounts/create
    // @Valid on the body — triggers @NotBlank and @Pattern on CreateAccountRequest.
    // @NotBlank on X-User-Uuid — rejects missing header with 400 immediately.
    // In production, API Gateway injects this header after JWT validation.
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            @RequestHeader("X-User-Uuid")
            @NotBlank(message = "X-User-Uuid header is required") String userUuid) {

        log.info("Create account request — type: {}, userUuid: {}",
                request.getAccountType(), userUuid);

        AccountResponse response = accountService.createAccount(request, userUuid);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Account created successfully"));
    }

    // GET /api/accounts/{accountNumber}
    // @PathVariable extracts the account number from the URL path.
    // e.g. GET /api/accounts/SBP0000000001 → accountNumber = "SBP0000000001"
    @GetMapping("/{accountNumber}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(
            @PathVariable String accountNumber,
            @RequestHeader("X-User-Uuid")
            @NotBlank(message = "X-User-Uuid header is required") String userUuid) {

        log.debug("Get account request — accountNumber: {}", accountNumber);
        AccountResponse response = accountService.getAccount(accountNumber, userUuid);
        return ResponseEntity.ok(
                ApiResponse.success(response, "Account fetched successfully"));
    }

    // GET /api/accounts/user/{userUuid}
    // Lists all accounts belonging to a specific user.
    // Service layer verifies the requesting user matches the path userUuid.
    @GetMapping("/user/{userUuid}")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccountsByUser(
            @PathVariable String userUuid,
            @RequestHeader("X-User-Uuid")
            @NotBlank(message = "X-User-Uuid header is required")
            String requestingUserUuid) {

        log.debug("List accounts request for userUuid: {}", userUuid);
        List<AccountResponse> accounts =
                accountService.getAccountsByUser(userUuid, requestingUserUuid);
        return ResponseEntity.ok(
                ApiResponse.success(accounts,
                        "Accounts fetched successfully. Count: " + accounts.size()));
    }

    // GET /api/accounts/{accountNumber}/balance
    // Lightweight endpoint — returns only balance, currency, status.
    // Called frequently by transaction-service to verify funds before transfer.
    @GetMapping("/{accountNumber}/balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @PathVariable String accountNumber,
            @RequestHeader("X-User-Uuid")
            @NotBlank(message = "X-User-Uuid header is required") String userUuid) {

        log.debug("Balance request for account: {}", accountNumber);
        BalanceResponse response = accountService.getBalance(accountNumber, userUuid);
        return ResponseEntity.ok(
                ApiResponse.success(response, "Balance fetched successfully"));
    }
}