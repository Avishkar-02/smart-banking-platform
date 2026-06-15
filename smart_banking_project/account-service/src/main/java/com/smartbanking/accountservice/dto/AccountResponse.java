package com.smartbanking.accountservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// What we send back after create or get account operations.
// Contains all fields a client needs to display account information.
// Never includes internal DB id.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private String uuid;

    private String accountNumber;

    private String userUuid;

    private String accountType;
    private String currency;

    // BigDecimal in the response — Jackson serializes it as a number with decimals
    private BigDecimal balance;

    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}