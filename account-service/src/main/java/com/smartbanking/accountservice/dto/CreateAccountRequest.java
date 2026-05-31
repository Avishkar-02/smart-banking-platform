package com.smartbanking.accountservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

// What the client sends to create a new bank account.
// Minimal — we only need the account type and currency.
// The userUuid comes from the X-User-Uuid header, not the body.
// Balance starts at 0 — always. Never trust client-provided initial balance.

@Data
public class CreateAccountRequest {

    // @NotNull because accountType is an enum — @NotBlank won't work on enums.
    @NotBlank(message = "Account type is required")
    @Pattern(
            regexp = "SAVINGS|CURRENT|FIXED_DEPOSIT",
            message = "Account type must be SAVINGS, CURRENT, or FIXED_DEPOSIT"
    )
    private String accountType;

    @NotBlank(message = "Currency is required")
    @Pattern(
            regexp = "INR|USD|EUR",
            message = "Currency must be INR, USD, or EUR"
    )
    private String currency;
}