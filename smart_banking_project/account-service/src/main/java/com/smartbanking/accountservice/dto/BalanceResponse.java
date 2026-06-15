package com.smartbanking.accountservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Lightweight response for balance-only queries.
// No need to serialize the full account when only balance is needed.
// Used by transaction-service frequently during fund verification.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    private String accountNumber;
    private BigDecimal balance;
    private String currency;
    // Status matters for balance check — FROZEN account cannot transact
    private String status;
}