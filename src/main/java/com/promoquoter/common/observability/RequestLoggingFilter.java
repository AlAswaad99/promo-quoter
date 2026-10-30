package com.promoquoter.common.observability;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Pattern ACCEPTED_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(RequestContext.HEADER));
        RequestContext.bind(requestId);
        response.setHeader(RequestContext.HEADER, requestId);
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        LogEvent.HTTP_REQUEST_STARTED.at(log)
                .with("method", request.getMethod())
                .with("path", request.getRequestURI())
                .with("startedAt", startedAt)
                .message("request started");
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
            int status = response.getStatus();
            Level level = status >= 500 ? Level.ERROR : status >= 400 ? Level.WARN : Level.INFO;
            LogEvent.HTTP_REQUEST_COMPLETED.at(log, level)
                    .with("method", request.getMethod())
                    .with("path", request.getRequestURI())
                    .with("status", status)
                    .with("startedAt", startedAt)
                    .with("endedAt", Instant.now())
                    .with("durationMs", durationMs)
                    .message("request completed");
            RequestContext.clear();
        }
    }

    private static String resolveRequestId(String incoming) {
        if (incoming != null && ACCEPTED_REQUEST_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
