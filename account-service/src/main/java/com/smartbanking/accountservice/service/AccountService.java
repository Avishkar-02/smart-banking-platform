package com.smartbanking.accountservice.service;

import com.smartbanking.accountservice.dto.AccountResponse;
import com.smartbanking.accountservice.dto.BalanceResponse;
import com.smartbanking.accountservice.dto.CreateAccountRequest;
import com.smartbanking.accountservice.entity.Account;
import com.smartbanking.accountservice.repository.AccountRepository;
import com.smartbanking.common.event.AccountCreatedEvent;
import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // @Transactional — if account saves but Kafka publish fails,
    // the entire save is rolled back. Consistent state guaranteed.
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request,
                                         String userUuid) {

        log.info("Creating {} account for user UUID: {}",
                request.getAccountType(), userUuid);

        // Generate unique account number — uses DB sequence with pessimistic lock
        String accountNumber = accountNumberGenerator.generate();

        Account.AccountType accountType;
        try {
            accountType = Account.AccountType.valueOf(request.getAccountType());
        } catch (IllegalArgumentException e) {
            // This should never happen because @Pattern validates the value first.
            // This is a defensive fallback.
            log.error("Invalid account type: {}", request.getAccountType());
            throw new BusinessException(
                    ErrorCode.INTERNAL_SERVER_ERROR, HttpStatus.BAD_REQUEST,
                    "Invalid account type: " + request.getAccountType());
        }

        // Build the Account entity.
        // balance starts at ZERO — never trust client-provided balance.
        // uuid and timestamps set by @PrePersist automatically.
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .userUuid(userUuid)
                .accountType(accountType)
                .currency(request.getCurrency())
                .balance(BigDecimal.ZERO)
                .status(Account.AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Account saved — number: {}, UUID: {}",
                saved.getAccountNumber(), saved.getUuid());

// In AccountService — replace the kafkaTemplate.send call with:
        AccountCreatedEvent event = AccountCreatedEvent.builder()
                .accountNumber(saved.getAccountNumber())
                .userUuid(saved.getUserUuid())
                .accountType(saved.getAccountType().name())
                .currency(saved.getCurrency())
                .build();

        kafkaTemplate.send(
                "account-service.account.created",
                saved.getAccountNumber(),
                event);

        log.info("AccountCreatedEvent published for account: {}",
                saved.getAccountNumber());

        return mapToResponse(saved);
    }

    public AccountResponse getAccount(String accountNumber, String requestingUserUuid) {
        log.debug("Fetching account: {} for user: {}",
                accountNumber, requestingUserUuid);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn("Account not found: {}", accountNumber);
                    return new BusinessException(
                            ErrorCode.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND);
                });

        // Ownership check — a user can ONLY see their own accounts.
        // Even if someone guesses the account number, they cannot access it
        // unless their UUID matches.
        verifyOwnership(account, requestingUserUuid);

        return mapToResponse(account);
    }

    public List<AccountResponse> getAccountsByUser(String userUuid,
                                                   String requestingUserUuid) {

        log.debug("Fetching all accounts for user: {}", userUuid);

        // Users can only list their own accounts
        if (!userUuid.equals(requestingUserUuid)) {
            log.warn("Unauthorized account list attempt. Requester: {}, Target: {}",
                    requestingUserUuid, userUuid);
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }

        List<Account> accounts = accountRepository.findByUserUuid(userUuid);
        log.debug("Found {} accounts for user: {}", accounts.size(), userUuid);

        // Stream converts List<Account> to List<AccountResponse>
        // by applying mapToResponse to each account.
        return accounts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public BalanceResponse getBalance(String accountNumber,
                                      String requestingUserUuid) {

        log.debug("Fetching balance for account: {}", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCOUNT_NOT_FOUND, HttpStatus.NOT_FOUND));

        verifyOwnership(account, requestingUserUuid);

        return BalanceResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus().name())
                .build();
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    private void verifyOwnership(Account account, String requestingUserUuid) {
        if (!account.getUserUuid().equals(requestingUserUuid)) {
            log.warn("Ownership violation — account {} belongs to {}, requested by {}",
                    account.getAccountNumber(),
                    account.getUserUuid(),
                    requestingUserUuid);
            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED, HttpStatus.FORBIDDEN);
        }
    }

    // Maps Account entity to AccountResponse DTO.
    // Defined once — if fields change, update here only.
    private AccountResponse mapToResponse(Account account) {
        return AccountResponse.builder()
                .uuid(account.getUuid())
                .accountNumber(account.getAccountNumber())
                .userUuid(account.getUserUuid())
                .accountType(account.getAccountType().name())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }


}