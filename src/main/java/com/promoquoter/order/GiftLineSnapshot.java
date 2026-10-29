package com.promoquoter.order;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
record GiftLineSnapshot(
        @Column(name = "product_id", nullable = false) UUID productId,
        @Column(nullable = false, length = 200) String name,
        @Column(nullable = false, length = 100) String category,
        @Column(nullable = false) int qty,
        @Column(name = "promotion_id", nullable = false) UUID promotionId,
        @Column(name = "promotion_name", nullable = false, length = 200) String promotionName) {
}
