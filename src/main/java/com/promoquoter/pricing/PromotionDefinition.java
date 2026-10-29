package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PromotionDefinition(
        UUID id,
        PromotionType type,
        String name,
        boolean active,
        int priority,
        Instant createdAt,
        Set<CustomerSegment> eligibleSegments,
        String category,
        BigDecimal percent,
        UUID productId,
        Integer buyQty,
        Integer freeQty,
        Integer minQty) implements RuleInfo {

    public boolean isEligibleFor(CustomerSegment segment) {
        return active && (eligibleSegments.isEmpty() || eligibleSegments.contains(segment));
    }
}
