package com.smartbanking.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.util.List;

// Global CORS configuration for the Gateway.
// The browser sends a preflight OPTIONS request before any real
// GET/POST/PUT/DELETE to a different-origin server. Without this bean,
// Spring Cloud Gateway has no Access-Control-Allow-Origin header to
// return, so the browser blocks the actual request with a CORS error
// — even for "public" routes like /api/auth/login.
@Configuration
public class CorsConfig {

//    @Bean
//    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
//        return http
//                .csrf(ServerHttpSecurity.CsrfSpec::disable)
//                .authorizeExchange(exchanges -> exchanges
//                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
//                        .anyExchange().permitAll())
//                .build();
//    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Frontend dev server origin. Add production origin here later too.
        config.setAllowedOrigins(List.of("http://localhost:3000"));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Allow all request headers (Authorization, Content-Type, etc.)
        config.setAllowedHeaders(List.of("*"));

        // Expose these so frontend JS can read them from the response
        config.setExposedHeaders(List.of("Authorization", "X-Correlation-ID"));

        // Allow cookies/Authorization header to be sent cross-origin
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}