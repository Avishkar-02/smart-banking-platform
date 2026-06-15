package com.smartbanking.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// Base exception for every expected business error across all services.
// GlobalExceptionHandler in each service catches this one type
// and builds the correct ApiResponse automatically.
// You never write try-catch blocks in controllers.

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    // Most common — message comes from ErrorCode.defaultMessage
    public BusinessException(ErrorCode errorCode, HttpStatus httpStatus) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // Use when you need a custom message on top of the error code
    // e.g. "Account ACC0001234 not found" instead of generic message
    public BusinessException(ErrorCode errorCode, HttpStatus httpStatus,
                             String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}