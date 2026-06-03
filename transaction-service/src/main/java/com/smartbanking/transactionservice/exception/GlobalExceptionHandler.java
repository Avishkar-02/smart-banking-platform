package com.smartbanking.transactionservice.exception;

import com.smartbanking.common.dto.ApiResponse;
import com.smartbanking.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex) {
        log.error("Business exception — {}: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(
                        ex.getMessage(), ex.getErrorCode().name()));
    }

    // Handles @Valid failures on @RequestBody fields
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Request body validation failed: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, "VALIDATION_FAILED"));
    }

    // Handles @NotBlank on @RequestHeader (requires @Validated on the controller)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("Constraint violation: {}", message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, "VALIDATION_FAILED"));
    }

    // Handles completely absent required headers
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException ex) {
        String message = "Required header '" + ex.getHeaderName() + "' is missing";
        log.warn(message);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message, "VALIDATION_FAILED"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(
                        "An unexpected error occurred", "INTERNAL_SERVER_ERROR"));
    }
}