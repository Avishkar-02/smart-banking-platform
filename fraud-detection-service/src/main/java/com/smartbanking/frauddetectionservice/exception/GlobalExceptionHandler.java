package com.smartbanking.frauddetectionservice.exception;

import com.smartbanking.common.dto.ApiResponse;
import com.smartbanking.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex) {
        log.error("Business exception — {}: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode().name()));
    }

    // Handles @NotBlank on @PathVariable (requires @Validated on controller)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getMessage())
                .collect(java.util.stream.Collectors.joining(", "));
        log.warn("Constraint violation: {}", message);
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