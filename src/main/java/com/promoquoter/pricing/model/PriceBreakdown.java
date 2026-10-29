package com.promoquoter.pricing.model;

import java.math.BigDecimal;
import java.util.List;

import com.promoquoter.common.money.Money;

public record PriceBreakdown(
        CustomerSegment customerSegment,
        List<LineBreakdown> lines,
        List<AppliedPromotion> appliedPromotions,
        BigDecimal subtotal,
        BigDecimal totalDiscount,
        BigDecimal total,
        List<CartAdjustment> adjustments,
        BigDecimal shipping,
        List<GiftRequest> gifts,
        List<RuleViolation> violations,
        List<SkippedRule> skippedRules) {

    public PriceBreakdown(CustomerSegment customerSegment, List<LineBreakdown> lines,
            List<AppliedPromotion> appliedPromotions, BigDecimal subtotal, BigDecimal totalDiscount,
            BigDecimal total) {
        this(customerSegment, lines, appliedPromotions, subtotal, totalDiscount, total, List.of(), Money.ZERO,
                List.of(), List.of(), List.of());
    }
}
