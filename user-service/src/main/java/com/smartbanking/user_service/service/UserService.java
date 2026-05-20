package com.smartbanking.user_service.service;

import com.smartbanking.common.event.UserRegisteredEvent;
import com.smartbanking.common.exception.BusinessException;
import com.smartbanking.common.exception.ErrorCode;
import com.smartbanking.user_service.dto.*;
import com.smartbanking.user_service.entity.User;
import com.smartbanking.user_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // @Lazy on AuthenticationManager breaks circular dependency:
    // UserService → AuthenticationManager → DaoAuthenticationProvider
    // → UserService (circular!)
    // @Lazy delays injection until first actual use.
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Lazy AuthenticationManager authenticationManager,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.kafkaTemplate = kafkaTemplate;
    }

    // Spring Security calls this during login.
    // AuthenticationManager.authenticate() triggers this to load user,
    // then compares password hash automatically.
    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        log.debug("Loading user by email for Spring Security: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }

    // @Transactional — if save succeeds but Kafka throws,
    // DB save is rolled back. No partial states.
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registration started for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration rejected — email taken: {}", request.getEmail());
            throw new BusinessException(
                    ErrorCode.USER_ALREADY_EXISTS, HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                // BCrypt hash — never store plain text password
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .status(User.UserStatus.ACTIVE)
                .role(User.UserRole.CUSTOMER)
                .kycApproved(false)
                .build();
        // uuid, createdAt, updatedAt set automatically by @PrePersist

        User saved = userRepository.save(user);
        log.info("User saved — UUID: {}", saved.getUuid());

        String accessToken = jwtService.generateAccessToken(
                saved.getUuid(), saved.getEmail(), saved.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(saved.getUuid());

        // Fire and forget — we don't wait for notification-service to send email.
        // Even if Kafka is slow, the user gets their tokens immediately.
        kafkaTemplate.send(
                "user-service.user.registered",
                saved.getUuid(),
                UserRegisteredEvent.builder()
                        .userUuid(saved.getUuid())
                        .email(saved.getEmail())
                        .firstName(saved.getFirstName())
                        .lastName(saved.getLastName())
                        .build());

        log.info("Registration complete — UUID: {}", saved.getUuid());
        return buildAuthResponse(saved, accessToken, refreshToken);
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            // Internally calls loadUserByUsername + BCrypt.matches()
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()));
        } catch (BadCredentialsException e) {
            // Generic message — never reveal whether email or password was wrong.
            // Telling attacker which is wrong helps them enumerate valid emails.
            log.warn("Login failed for email: {}", request.getEmail());
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS, HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            log.warn("Login blocked — account {} status: {}",
                    user.getUuid(), user.getStatus());
            throw new BusinessException(
                    ErrorCode.USER_ACCOUNT_SUSPENDED, HttpStatus.FORBIDDEN);
        }

        String accessToken = jwtService.generateAccessToken(
                user.getUuid(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getUuid());

        log.info("Login successful — UUID: {}", user.getUuid());
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    public void logout(String token) {
        jwtService.blacklistToken(token);
        log.info("User logged out — token blacklisted");
    }

    public UserProfileDto getMyProfile() {
        // JWT filter stored the userUuid as principal in SecurityContext.
        // We read it here — no need to pass UUID in request body or path.
        String userUuid = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        log.debug("Fetching profile for UUID: {}", userUuid);

        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));

        return mapToProfileDto(user);
    }

    private UserProfileDto mapToProfileDto(User user) {
        return UserProfileDto.builder()
                .uuid(user.getUuid())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .status(user.getStatus().name())
                .role(user.getRole().name())
                .kycApproved(user.isKycApproved())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AuthResponse buildAuthResponse(User user, String accessToken,
                                           String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationTime())
                .userUuid(user.getUuid())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .build();
    }
}