package com.promoquoter.common.error;

import java.net.URI;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    INVALID_SORT(HttpStatus.BAD_REQUEST, "Invalid sort"),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Invalid idempotency key"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Product not found"),
    PROMOTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Promotion not found"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Order not found"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Insufficient stock"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    UNKNOWN_PRODUCT(HttpStatus.UNPROCESSABLE_ENTITY, "Unknown product"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused"),
    RULE_VIOLATION(HttpStatus.UNPROCESSABLE_ENTITY, "Rule violation"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus status;
    private final String title;

    public URI type() {
        return URI.create("urn:promoquoter:error:" + name().toLowerCase().replace('_', '-'));
    }
}
