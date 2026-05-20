package com.smartbanking.user_service.filter;

import com.smartbanking.user_service.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// OncePerRequestFilter — runs exactly once per request, guaranteed.

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token or wrong format — pass through.
        // SecurityConfig decides if endpoint is public or needs auth.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Strip "Bearer " prefix — 7 characters
        String token = authHeader.substring(7);

        if (!jwtService.isTokenValid(token)) {
            log.warn("Invalid JWT rejected for: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String userUuid = jwtService.extractUserUuid(token);
        String role = jwtService.extractRole(token);

        // Tell Spring Security this request is authenticated.
        // principal = userUuid, credentials = null (already verified by token),
        // authorities = ROLE_CUSTOMER or ROLE_ADMIN
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userUuid,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request));

        // Store in SecurityContext — all code in this request thread
        // can now get the current user's UUID from here.
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated user UUID: {} for: {}", userUuid,
                request.getRequestURI());

        filterChain.doFilter(request, response);
    }
}