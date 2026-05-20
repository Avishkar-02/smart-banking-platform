package com.smartbanking.user_service.controller;

import com.smartbanking.common.dto.ApiResponse;
import com.smartbanking.user_service.dto.*;
import com.smartbanking.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // POST /api/auth/register
    // @Valid triggers validation on RegisterRequest before service is called
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        log.info("Register request for email: {}", request.getEmail());
        AuthResponse response = userService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Registration successful"));
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        log.info("Login request for email: {}", request.getEmail());
        AuthResponse response = userService.login(request);
        return ResponseEntity.ok(
                ApiResponse.success(response, "Login successful"));
    }

    // POST /api/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader) {
        // authHeader = "Bearer eyJhbGci..." — strip the prefix
        String token = authHeader.substring(7);
        userService.logout(token);
        return ResponseEntity.ok(
                ApiResponse.success(null, "Logged out successfully"));
    }

    // GET /api/users/profile
    // UUID comes from the JWT token via SecurityContext — not from request params.
    // User can only ever get their own profile.
    @GetMapping("/users/profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> getMyProfile() {
        UserProfileDto profile = userService.getMyProfile();
        return ResponseEntity.ok(
                ApiResponse.success(profile, "Profile fetched successfully"));
    }
}