package com.smartbanking.user_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.common.exception.ErrorCode;
import com.smartbanking.user_service.dto.AuthResponse;
import com.smartbanking.user_service.dto.LoginRequest;
import com.smartbanking.user_service.dto.RegisterRequest;
import com.smartbanking.user_service.filter.JwtAuthenticationFilter;
import com.smartbanking.user_service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
// @WebMvcTest loads ONLY the web layer — controllers + exception handlers.
// No services, repositories, Kafka, Redis, DB.
// Much faster than @SpringBootTest.
// We exclude JwtAuthenticationFilter so tests don't need a real token.

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@DisplayName("UserController Tests")
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    // Needed by SecurityConfig even in @WebMvcTest
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("POST /register — 201 with tokens on success")
    void shouldRegister201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Avishkar");
        request.setLastName("Suryawanshi");
        request.setEmail("avishkar@test.com");
        request.setPassword("password123");

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(3600000L)
                .userUuid("uuid-123")
                .email("avishkar@test.com")
                .firstName("Avishkar")
                .lastName("Suryawanshi")
                .role("CUSTOMER")
                .build();

        when(userService.register(any(RegisterRequest.class)))
                .thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.userUuid").value("uuid-123"));
    }

    @Test
    @DisplayName("POST /register — 400 when email is invalid format")
    void shouldReturn400OnInvalidEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Avishkar");
        request.setLastName("Suryawanshi");
        request.setEmail("not-an-email");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    @Test
    @DisplayName("POST /register — 409 when email already exists")
    void shouldReturn409OnDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Avishkar");
        request.setLastName("Suryawanshi");
        request.setEmail("existing@test.com");
        request.setPassword("password123");

        when(userService.register(any()))
                .thenThrow(new BusinessException(
                        ErrorCode.USER_ALREADY_EXISTS, HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /login — 200 with tokens on success")
    void shouldLogin200() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("avishkar@test.com");
        request.setPassword("password123");

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("access-token")
                .email("avishkar@test.com")
                .userUuid("uuid-123")
                .build();

        when(userService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"));
    }

    @Test
    @DisplayName("POST /login — 401 on wrong credentials")
    void shouldReturn401OnBadCredentials() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("avishkar@test.com");
        request.setPassword("wrong");

        when(userService.login(any()))
                .thenThrow(new BusinessException(
                        ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /register — 400 when password too short")
    void shouldReturn400OnShortPassword() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Avishkar");
        request.setLastName("Suryawanshi");
        request.setEmail("avishkar@test.com");
        request.setPassword("short");

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}