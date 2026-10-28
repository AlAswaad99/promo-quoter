package com.promoquoter.common.observability;

import java.util.Optional;

import org.slf4j.MDC;

public final class RequestContext {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestContext() {
    }

    public static Optional<String> currentRequestId() {
        return Optional.ofNullable(MDC.get(MDC_KEY)).filter(id -> !id.isBlank());
    }

    static void bind(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    static void clear() {
        MDC.remove(MDC_KEY);
    }
}
