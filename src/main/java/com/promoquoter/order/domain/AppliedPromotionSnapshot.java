package com.promoquoter.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.promoquoter.pricing.model.PromotionType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
record AppliedPromotionSnapshot(
        @Column(name = "sequence_no", nullable = false) int sequence,
        @Column(name = "promotion_id", nullable = false) UUID promotionId,
        @Column(nullable = false, length = 200) String name,
        @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) PromotionType type,
        @Column(name = "product_id", nullable = false) UUID productId,
        @Column(nullable = false, precision = 19, scale = 2) BigDecimal amount) {
}
