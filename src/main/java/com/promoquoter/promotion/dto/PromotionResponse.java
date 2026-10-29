package com.promoquoter.promotion.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.PromotionType;
import com.promoquoter.pricing.model.RuleKind;

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
}
