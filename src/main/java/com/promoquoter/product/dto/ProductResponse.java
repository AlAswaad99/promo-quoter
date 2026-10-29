package com.promoquoter.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.promoquoter.common.money.MoneyField;

public record ProductResponse(
        UUID id,
        String name,
        String category,
        @MoneyField BigDecimal price,
        int stock,
        Instant createdAt) {
}
