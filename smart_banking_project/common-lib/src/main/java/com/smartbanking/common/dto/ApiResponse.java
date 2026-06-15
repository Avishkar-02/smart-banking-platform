package com.smartbanking.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

// JsonInclude.NON_NULL — fields that are null won't appear in the JSON output.
// So if there's no errorCode, that field simply won't show. Clean responses.

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    // "SUCCESS" or "ERROR" — caller knows result without parsing data
    private String status;

    // Human-readable message for frontend display and debugging
    private String message;

    // The actual payload — generic T means this works for any type:
    // ApiResponse<AuthResponse>, ApiResponse<List<AccountDto>> etc.
    private T data;

    // Machine-readable error code — frontend switches on this to show right UI
    // Only present on errors. JsonInclude.NON_NULL hides it on success.
    private String errorCode;

    // Every response carries a timestamp for debugging
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Static factory methods — readable at call site:
    // ApiResponse.success(data, "message") instead of builder chain everywhere
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .status("ERROR")
                .message(message)
                .errorCode(errorCode)
                .build();
    }
}