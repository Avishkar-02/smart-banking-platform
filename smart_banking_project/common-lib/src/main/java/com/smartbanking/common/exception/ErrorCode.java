package com.smartbanking.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // User errors
    USER_NOT_FOUND("User not found"),
    USER_ALREADY_EXISTS("User with this email already exists"),
    INVALID_CREDENTIALS("Invalid email or password"),
    USER_ACCOUNT_SUSPENDED("Your account has been suspended"),

    // Auth errors
    INVALID_TOKEN("Token is invalid or expired"),
    TOKEN_REVOKED("Token has been revoked"),
    UNAUTHORIZED("You are not authorized to perform this action"),

    // Account errors
    ACCOUNT_NOT_FOUND("Bank account not found"),
    ACCOUNT_FROZEN("This account has been frozen"),
    ACCOUNT_CLOSED("This account is closed"),

    // Transaction errors
    INSUFFICIENT_BALANCE("Insufficient balance for this transfer"),
    DUPLICATE_TRANSACTION("Transaction with this idempotency key already processed"),
    TRANSACTION_NOT_FOUND("Transaction not found"),
    INVALID_TRANSFER_AMOUNT("Transfer amount must be greater than zero"),
    SAME_ACCOUNT_TRANSFER("Source and destination accounts cannot be the same"),

    // System errors
    RATE_LIMIT_EXCEEDED("Too many requests. Please try again later"),
    INTERNAL_SERVER_ERROR("An unexpected error occurred"),
    SERVICE_UNAVAILABLE("Service temporarily unavailable");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }
}