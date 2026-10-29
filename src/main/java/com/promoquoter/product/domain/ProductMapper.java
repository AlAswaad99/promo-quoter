package com.promoquoter.product.domain;

import com.promoquoter.product.dto.ProductResponse;

final class ProductMapper {

    private ProductMapper() {
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getCategory(), product.getPrice(),
                product.getStock(), product.getCreatedAt());
    }
}
