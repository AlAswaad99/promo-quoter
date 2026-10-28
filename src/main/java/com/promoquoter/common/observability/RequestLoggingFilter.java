package com.promoquoter.common.observability;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

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
        log.info("request started method={} path={} startedAt={}", request.getMethod(), request.getRequestURI(),
                startedAt);
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;
            int status = response.getStatus();
            String summary = "request completed method={} path={} status={} startedAt={} endedAt={} durationMs={}";
            Object[] fields = {request.getMethod(), request.getRequestURI(), status, startedAt, Instant.now(),
                    durationMs};
            if (status >= 500) {
                log.error(summary, fields);
            } else if (status >= 400) {
                log.warn(summary, fields);
            } else {
                log.info(summary, fields);
            }
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
