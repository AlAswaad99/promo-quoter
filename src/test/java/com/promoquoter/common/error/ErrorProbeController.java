package com.promoquoter.common.error;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/test-support")
class ErrorProbeController {

    record Payload(@NotBlank String name, @Min(1) int qty) {
    }

    record Shortage(String productId, int requested, int available) {
    }

    @PostMapping("/body")
    void body(@RequestBody @Valid Payload payload) {
    }

    @PostMapping("/list")
    void list(@RequestBody @Size(min = 1, max = 2) List<@Valid Payload> items) {
    }

    @GetMapping("/param")
    void param(@RequestParam @Min(1) int n) {
    }

    @GetMapping("/items/{id}")
    void item(@PathVariable UUID id) {
    }

    @GetMapping("/api-error")
    void apiError() {
        throw new ApiException(ErrorCode.INSUFFICIENT_STOCK, "Not enough stock",
                List.of(new Shortage("p-1", 5, 2)));
    }

    @GetMapping("/plain-api-error")
    void plainApiError() {
        throw new ApiException(ErrorCode.PRODUCT_NOT_FOUND, "Product missing");
    }

    @GetMapping("/lock")
    void lock() {
        throw new CannotAcquireLockException("row locked");
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("secret internals");
    }
}
