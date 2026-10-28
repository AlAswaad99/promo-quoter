package com.promoquoter.product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(UUID id, String name, String category, BigDecimal price) {

    static ProductSnapshot from(Product product) {
        return new ProductSnapshot(product.getId(), product.getName(), product.getCategory(), product.getPrice());
    }
}
