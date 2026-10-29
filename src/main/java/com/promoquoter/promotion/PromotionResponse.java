package com.promoquoter.promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.RuleDefinition;
import com.promoquoter.pricing.RuleKind;

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
        Integer minQty,
        Instant createdAt,
        RuleKind kind,
        int stage,
        boolean stopAfter,
        JsonNode when,
        JsonNode target,
        JsonNode action) {

    static PromotionResponse from(Promotion promotion) {
        RuleDefinition rule = promotion.toDefinition();
        return new PromotionResponse(promotion.getId(), promotion.getType(), promotion.getName(),
                promotion.isActive(), promotion.getPriority(),
                promotion.getEligibleSegments().stream().sorted().toList(), promotion.getCategory(),
                promotion.getPercent(), promotion.getProductId(), promotion.getBuyQty(), promotion.getFreeQty(),
                promotion.getMinQty(), promotion.getCreatedAt(), rule.kind(), rule.stage(), rule.stopAfter(),
                RuleJsonWriter.condition(rule.when()), RuleJsonWriter.condition(rule.target()),
                RuleJsonWriter.action(rule.action()));
    }
}
