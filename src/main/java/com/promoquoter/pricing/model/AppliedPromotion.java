package com.promoquoter.pricing.model;

import java.math.BigDecimal;
import java.util.UUID;

public record AppliedPromotion(
        int sequence,
        UUID promotionId,
        String name,
        PromotionType type,
        UUID productId,
        BigDecimal amount) {
}
