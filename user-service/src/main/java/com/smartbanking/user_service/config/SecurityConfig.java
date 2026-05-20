package com.smartbanking.user_service.config;

import com.smartbanking.user_service.filter.JwtAuthenticationFilter;
import com.smartbanking.user_service.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final UserService userService;

    // @Lazy on UserService breaks the circular dependency:
    // SecurityConfig → UserService → AuthenticationManager → SecurityConfig
    // @Lazy delays UserService injection until first use, breaking the cycle.
    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter,
                          @Lazy UserService userService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userService = userService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength 12 = 2^12 iterations. ~300ms per hash.
        // Fast enough for users, painful for brute-force attackers.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        // Tells Spring HOW to load users (by email) and HOW to verify passwords (BCrypt)
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF — only for browser session/cookie apps.
                // We use stateless JWT — no sessions, no cookies. CSRF doesn't apply.
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no token required
                        .requestMatchers("/api/auth/**", "/actuator/**").permitAll()
                        // Everything else needs a valid JWT
                        .anyRequest().authenticated()
                )

                // STATELESS — no HTTP sessions ever created or used.
                // Every request must carry JWT independently.
                // Any of our service instances can handle any request.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authenticationProvider(authenticationProvider())

                // Our JWT filter runs BEFORE Spring's default auth filter.
                // It reads the token and sets authentication in SecurityContext.
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}