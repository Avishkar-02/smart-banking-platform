package com.smartbanking.transactionservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

// What the client sends to initiate a transfer.
// Every field is validated before the service method is even called.

@Data
public class TransferRequest {

    // Account number format: SBP followed by exactly 10 digits
    @NotBlank(message = "Source account number is required")
    @Pattern(
            regexp = "SBP[0-9]{10}",
            message = "Invalid account number format. Expected: SBP followed by 10 digits"
    )
    private String sourceAccountNumber;

    @NotBlank(message = "Destination account number is required")
    @Pattern(
            regexp = "SBP[0-9]{10}",
            message = "Invalid account number format. Expected: SBP followed by 10 digits"
    )
    private String destinationAccountNumber;

    // @NotNull because @DecimalMin does not work on null values.
    // We need both annotations.
    @NotNull(message = "Amount is required")
    // inclusive = false means amount must be STRICTLY greater than 0.01
    // "0.01" as string is what @DecimalMin expects
    @DecimalMin(value = "0.01", inclusive = true,
            message = "Transfer amount must be at least 0.01")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Pattern(
            regexp = "INR|USD|EUR",
            message = "Currency must be INR, USD, or EUR"
    )
    private String currency;

    // Optional sender note — not validated strictly
    private String description;
}