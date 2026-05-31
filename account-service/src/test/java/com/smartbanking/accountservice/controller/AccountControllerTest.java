package com.smartbanking.accountservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbanking.accountservice.dto.AccountResponse;
import com.smartbanking.accountservice.dto.BalanceResponse;
import com.smartbanking.accountservice.dto.CreateAccountRequest;
import com.smartbanking.accountservice.service.AccountService;
import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// @WebMvcTest loads ONLY the web layer — controllers, exception handlers.
// No service, no repository, no Kafka, no DB needed.
// MockMvc sends HTTP requests in-memory — no real server running.

@ActiveProfiles("test")
@WebMvcTest(AccountController.class)
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false"
})
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AccountService accountService;

    private static final String USER_UUID = "user-uuid-123";
    private static final String ACCOUNT_NUMBER = "SBP0000000001";

    // Helper to build a sample AccountResponse for mock returns
    private AccountResponse sampleAccountResponse() {
        return AccountResponse.builder()
                .uuid("account-uuid-456")
                .accountNumber(ACCOUNT_NUMBER)
                .userUuid(USER_UUID)
                .accountType("SAVINGS")
                .currency("INR")
                .balance(BigDecimal.ZERO)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── POST /api/accounts/create ────────────────────────────────────────────

    @Test
    @DisplayName("POST /create — 201 with account details on success")
    void shouldCreateAccount201() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType("SAVINGS");
        request.setCurrency("INR");

        when(accountService.createAccount(any(), anyString()))
                .thenReturn(sampleAccountResponse());

        mockMvc.perform(post("/api/accounts/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Uuid", USER_UUID)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /create — 400 when X-User-Uuid header is missing")
    void shouldReturn400WhenHeaderMissing() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType("SAVINGS");
        request.setCurrency("INR");

        mockMvc.perform(post("/api/accounts/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        // No X-User-Uuid header
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /create — 400 when accountType is invalid")
    void shouldReturn400WhenAccountTypeInvalid() throws Exception {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setAccountType("INVALID_TYPE"); // Not in pattern
        request.setCurrency("INR");

        mockMvc.perform(post("/api/accounts/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-User-Uuid", USER_UUID)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ── GET /api/accounts/{accountNumber} ────────────────────────────────────

    @Test
    @DisplayName("GET /{accountNumber} — 200 with account details")
    void shouldGetAccount200() throws Exception {
        when(accountService.getAccount(anyString(), anyString()))
                .thenReturn(sampleAccountResponse());

        mockMvc.perform(get("/api/accounts/" + ACCOUNT_NUMBER)
                        .header("X-User-Uuid", USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.data.userUuid").value(USER_UUID));
    }

    @Test
    @DisplayName("GET /{accountNumber} — 404 when account not found")
    void shouldReturn404WhenAccountNotFound() throws Exception {
        when(accountService.getAccount(anyString(), anyString()))
                .thenThrow(new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/accounts/SBP9999999999")
                        .header("X-User-Uuid", USER_UUID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /{accountNumber} — 403 when account belongs to different user")
    void shouldReturn403WhenNotOwner() throws Exception {
        when(accountService.getAccount(anyString(), anyString()))
                .thenThrow(new BusinessException(
                        ErrorCode.UNAUTHORIZED, HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/accounts/" + ACCOUNT_NUMBER)
                        .header("X-User-Uuid", "attacker-uuid"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // ── GET /api/accounts/user/{userUuid} ────────────────────────────────────

    @Test
    @DisplayName("GET /user/{userUuid} — 200 with list of accounts")
    void shouldListAccounts200() throws Exception {
        when(accountService.getAccountsByUser(anyString(), anyString()))
                .thenReturn(List.of(sampleAccountResponse()));

        mockMvc.perform(get("/api/accounts/user/" + USER_UUID)
                        .header("X-User-Uuid", USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                // $.data is an array — $.data[0] is the first element
                .andExpect(jsonPath("$.data[0].accountNumber").value(ACCOUNT_NUMBER));
    }

    @Test
    @DisplayName("GET /user/{userUuid} — 200 with empty list when no accounts")
    void shouldReturn200WithEmptyListWhenNoAccounts() throws Exception {
        when(accountService.getAccountsByUser(anyString(), anyString()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/accounts/user/" + USER_UUID)
                        .header("X-User-Uuid", USER_UUID))
                .andExpect(status().isOk())
                // Empty array — not 404
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── GET /api/accounts/{accountNumber}/balance ─────────────────────────────

    @Test
    @DisplayName("GET /{accountNumber}/balance — 200 with balance")
    void shouldGetBalance200() throws Exception {
        BalanceResponse balanceResponse = BalanceResponse.builder()
                .accountNumber(ACCOUNT_NUMBER)
                .balance(new BigDecimal("50000.0000"))
                .currency("INR")
                .status("ACTIVE")
                .build();

        when(accountService.getBalance(anyString(), anyString()))
                .thenReturn(balanceResponse);

        mockMvc.perform(get("/api/accounts/" + ACCOUNT_NUMBER + "/balance")
                        .header("X-User-Uuid", USER_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(50000.0000))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.accountNumber").value(ACCOUNT_NUMBER));
    }
}