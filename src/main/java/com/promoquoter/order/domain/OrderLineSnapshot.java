package com.promoquoter.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
record OrderLineSnapshot(
        @Column(name = "product_id", nullable = false) UUID productId,
        @Column(nullable = false, length = 200) String name,
        @Column(nullable = false, length = 100) String category,
        @Column(name = "unit_price", nullable = false, precision = 19, scale = 2) BigDecimal unitPrice,
        @Column(nullable = false) int qty,
        @Column(nullable = false, precision = 19, scale = 2) BigDecimal subtotal,
        @Column(nullable = false, precision = 19, scale = 2) BigDecimal discount,
        @Column(nullable = false, precision = 19, scale = 2) BigDecimal total) {
}
