package com.promoquoter.promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PromotionType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromotionResponse(
        UUID id,
        PromotionType type,
        String name,
        boolean active,
        int priority,
        List<CustomerSegment> eligibleSegments,
        String category,
        BigDecimal percent,
        UUID productId,
        Integer buyQty,
        Integer freeQty,
        Instant createdAt) {

    static PromotionResponse from(Promotion promotion) {
        return new PromotionResponse(promotion.getId(), promotion.getType(), promotion.getName(),
                promotion.isActive(), promotion.getPriority(),
                promotion.getEligibleSegments().stream().sorted().toList(), promotion.getCategory(),
                promotion.getPercent(), promotion.getProductId(), promotion.getBuyQty(), promotion.getFreeQty(),
                promotion.getCreatedAt());
    }
}
