package com.promoquoter.product;

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

    static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getCategory(), product.getPrice(),
                product.getStock(), product.getCreatedAt());
    }
}
