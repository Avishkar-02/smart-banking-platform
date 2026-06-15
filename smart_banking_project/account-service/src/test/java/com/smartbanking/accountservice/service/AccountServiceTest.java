package com.smartbanking.accountservice.service;

import com.smartbanking.accountservice.dto.AccountResponse;
import com.smartbanking.accountservice.dto.BalanceResponse;
import com.smartbanking.accountservice.dto.CreateAccountRequest;
import com.smartbanking.accountservice.entity.Account;
import com.smartbanking.accountservice.repository.AccountRepository;
import com.smartbanking.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AccountNumberGenerator accountNumberGenerator;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AccountService accountService;

    private static final String USER_UUID = "user-uuid-123";
    private static final String ACCOUNT_NUMBER = "SBP0000000001";
    private static final String ACCOUNT_UUID = "account-uuid-456";

    private Account activeAccount;
    private CreateAccountRequest createRequest;

    @BeforeEach
    void setUp() {
        // Reusable active account for most tests
        activeAccount = Account.builder()
                .id(1L)
                .uuid(ACCOUNT_UUID)
                .accountNumber(ACCOUNT_NUMBER)
                .userUuid(USER_UUID)
                .accountType(Account.AccountType.SAVINGS)
                .currency("INR")
                .balance(BigDecimal.ZERO)
                .status(Account.AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        createRequest = new CreateAccountRequest();
        createRequest.setAccountType("SAVINGS");
        createRequest.setCurrency("INR");
    }

    // ── CREATE ACCOUNT ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Should create account with zero balance and correct fields")
    void shouldCreateAccountSuccessfully() {
        when(accountNumberGenerator.generate()).thenReturn(ACCOUNT_NUMBER);
        when(accountRepository.save(any(Account.class))).thenReturn(activeAccount);

        AccountResponse response = accountService.createAccount(createRequest, USER_UUID);

        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(response.getUserUuid()).isEqualTo(USER_UUID);
        assertThat(response.getBalance()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getAccountType()).isEqualTo("SAVINGS");

        // Verify account was saved
        verify(accountRepository, times(1)).save(any(Account.class));
        // Verify Kafka event was published
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should always start balance at ZERO regardless of anything")
    void shouldAlwaysStartWithZeroBalance() {
        when(accountNumberGenerator.generate()).thenReturn(ACCOUNT_NUMBER);
        when(accountRepository.save(any(Account.class))).thenReturn(activeAccount);

        AccountResponse response = accountService.createAccount(createRequest, USER_UUID);

        // Balance MUST be zero — never trust any other value
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ── GET ACCOUNT ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return account when owner requests it")
    void shouldGetAccountForOwner() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER))
                .thenReturn(Optional.of(activeAccount));

        AccountResponse response = accountService.getAccount(ACCOUNT_NUMBER, USER_UUID);

        assertThat(response.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(response.getUserUuid()).isEqualTo(USER_UUID);
    }

    @Test
    @DisplayName("Should throw ACCOUNT_NOT_FOUND for non-existent account number")
    void shouldThrowWhenAccountNotFound() {
        when(accountRepository.findByAccountNumber(anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                accountService.getAccount("SBP9999999999", USER_UUID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when different user requests account")
    void shouldThrowWhenNotOwner() {
        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER))
                .thenReturn(Optional.of(activeAccount));

        // Different user UUID — not the owner
        assertThatThrownBy(() ->
                accountService.getAccount(ACCOUNT_NUMBER, "different-user-uuid"))
                .isInstanceOf(BusinessException.class);
    }

    // ── GET ACCOUNTS BY USER ─────────────────────────────────────────────────

    @Test
    @DisplayName("Should return list of accounts for correct user")
    void shouldReturnAccountListForOwner() {
        when(accountRepository.findByUserUuid(USER_UUID))
                .thenReturn(List.of(activeAccount));

        List<AccountResponse> accounts =
                accountService.getAccountsByUser(USER_UUID, USER_UUID);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getUserUuid()).isEqualTo(USER_UUID);
    }

    @Test
    @DisplayName("Should return empty list when user has no accounts")
    void shouldReturnEmptyListWhenNoAccounts() {
        when(accountRepository.findByUserUuid(USER_UUID))
                .thenReturn(List.of());

        List<AccountResponse> accounts =
                accountService.getAccountsByUser(USER_UUID, USER_UUID);

        // Empty list — not an error
        assertThat(accounts).isEmpty();
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED when user queries another user's accounts")
    void shouldThrowWhenListingAnotherUsersAccounts() {
        assertThatThrownBy(() ->
                accountService.getAccountsByUser(USER_UUID, "attacker-uuid"))
                .isInstanceOf(BusinessException.class);

        // Repository should never be called — rejected before DB query
        verify(accountRepository, never()).findByUserUuid(anyString());
    }

    // ── GET BALANCE ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return balance for account owner")
    void shouldReturnBalance() {
        Account accountWithBalance = Account.builder()
                .id(1L).uuid(ACCOUNT_UUID).accountNumber(ACCOUNT_NUMBER)
                .userUuid(USER_UUID).accountType(Account.AccountType.SAVINGS)
                .currency("INR").balance(new BigDecimal("50000.0000"))
                .status(Account.AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();

        when(accountRepository.findByAccountNumber(ACCOUNT_NUMBER))
                .thenReturn(Optional.of(accountWithBalance));

        BalanceResponse response =
                accountService.getBalance(ACCOUNT_NUMBER, USER_UUID);

        assertThat(response.getBalance()).isEqualByComparingTo(
                new BigDecimal("50000.0000"));
        assertThat(response.getCurrency()).isEqualTo("INR");
        assertThat(response.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
    }
}