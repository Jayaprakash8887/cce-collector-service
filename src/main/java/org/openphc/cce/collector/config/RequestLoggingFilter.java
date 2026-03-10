package org.openphc.cce.collector.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes MDC context for structured logging.
 *
 * <p>Sets a unique {@code requestId} in the MDC for every HTTP request.
 * Additional MDC fields ({@code correlationId}, {@code cloudEventsId},
 * {@code source}, {@code subject}) are populated later by
 * {@code EventIngestionService} once the request body is parsed.</p>
 *
 * <p>The MDC is cleared at the end of each request to prevent leakage
 * to subsequent requests on pooled threads.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
