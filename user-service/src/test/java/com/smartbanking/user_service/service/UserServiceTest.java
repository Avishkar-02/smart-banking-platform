package com.smartbanking.user_service.service;

import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.user_service.dto.AuthResponse;
import com.smartbanking.user_service.dto.LoginRequest;
import com.smartbanking.user_service.dto.RegisterRequest;
import com.smartbanking.user_service.entity.User;
import com.smartbanking.user_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserService userService;

    private RegisterRequest registerRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setFirstName("Avishkar");
        registerRequest.setLastName("Suryawanshi");
        registerRequest.setEmail("avishkar@test.com");
        registerRequest.setPassword("password123");

        savedUser = User.builder()
                .id(1L)
                .uuid("test-uuid-123")
                .firstName("Avishkar")
                .lastName("Suryawanshi")
                .email("avishkar@test.com")
                .password("$2a$12$hashedpassword")
                .status(User.UserStatus.ACTIVE)
                .role(User.UserRole.CUSTOMER)
                .kycApproved(false)
                .build();
    }

    @Test
    @DisplayName("Should register user and return tokens")
    void shouldRegisterSuccessfully() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hash");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateAccessToken(anyString(), anyString(), anyString()))
                .thenReturn("access-token");
        when(jwtService.generateRefreshToken(anyString()))
                .thenReturn("refresh-token");
        when(jwtService.getExpirationTime()).thenReturn(3600000L);

        AuthResponse response = userService.register(registerRequest);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUserUuid()).isEqualTo("test-uuid-123");
        assertThat(response.getEmail()).isEqualTo("avishkar@test.com");

        // Verify user was saved exactly once
        verify(userRepository, times(1)).save(any(User.class));
        // Verify Kafka event was published
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should throw CONFLICT when email already exists")
    void shouldThrowWhenEmailTaken() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.register(registerRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        // Nothing should be saved or published
        verify(userRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should login with correct credentials")
    void shouldLoginSuccessfully() {
        LoginRequest request = new LoginRequest();
        request.setEmail("avishkar@test.com");
        request.setPassword("password123");

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(savedUser));
        when(jwtService.generateAccessToken(anyString(), anyString(), anyString()))
                .thenReturn("access-token");
        when(jwtService.generateRefreshToken(anyString()))
                .thenReturn("refresh-token");
        when(jwtService.getExpirationTime()).thenReturn(3600000L);

        AuthResponse response = userService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUserUuid()).isEqualTo("test-uuid-123");
    }

    @Test
    @DisplayName("Should throw UNAUTHORIZED on wrong credentials")
    void shouldThrowOnBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("avishkar@test.com");
        request.setPassword("wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class);

        verify(jwtService, never())
                .generateAccessToken(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw FORBIDDEN when account is suspended")
    void shouldThrowWhenSuspended() {
        LoginRequest request = new LoginRequest();
        request.setEmail("avishkar@test.com");
        request.setPassword("password123");

        User suspendedUser = User.builder()
                .uuid("test-uuid-123")
                .email("avishkar@test.com")
                .password("$2a$12$hash")
                .status(User.UserStatus.SUSPENDED)
                .role(User.UserRole.CUSTOMER)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(suspendedUser));

        assertThatThrownBy(() -> userService.login(request))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Should call blacklistToken on logout")
    void shouldLogout() {
        userService.logout("some-token");
        verify(jwtService, times(1)).blacklistToken("some-token");
    }
}