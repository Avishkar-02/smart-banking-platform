package com.smartbanking.user_service.filter;

import com.smartbanking.common.util.CorrelationIdUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

// @Order(1) — this filter runs FIRST before any other filter including JWT filter.
// Why first? Because we want the correlation ID in logs from the very first moment
// the request enters our system — including any JWT validation log lines.

@Slf4j
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Try to get correlation ID from the incoming request header.
        // If request comes from API Gateway, the gateway already set this header.
        // If request comes directly (Postman in dev), header is absent — we generate one.
        String correlationId = request.getHeader(
                CorrelationIdUtils.CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = CorrelationIdUtils.generate();
            log.debug("No correlation ID in request — generated new one: {}", correlationId);
        }

        // MDC = Mapped Diagnostic Context.
        // It's a thread-local map — each request thread has its own copy.
        // Once you put a value here, every log statement on THIS thread
        // automatically includes it — no need to pass it to every method.
        // Our logging pattern in properties has %X{correlationId}
        // which reads from MDC automatically.
        MDC.put("correlationId", correlationId);

        // Add correlation ID to the response headers too.
        // This way, when the client gets a response, they can include
        // the correlation ID in a bug report and you can trace the request.
        response.setHeader(CorrelationIdUtils.CORRELATION_ID_HEADER, correlationId);

        try {
            // Continue processing the request
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL — always clear MDC after the request completes.
            // MDC uses ThreadLocal. In a thread pool, threads are reused.
            // If you don't clear, the next request on this thread
            // inherits the previous request's correlation ID — silent bug.
            MDC.clear();
        }
    }
}