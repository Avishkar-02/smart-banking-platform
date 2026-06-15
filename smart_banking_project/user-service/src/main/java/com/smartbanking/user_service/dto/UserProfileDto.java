package com.smartbanking.user_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// Safe read-only view — no password, no internal id.

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {

    private String uuid;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String status;
    private String role;
    private boolean kycApproved;
    private LocalDateTime createdAt;
}