package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.util.List;

public record PriceBreakdown(
        CustomerSegment customerSegment,
        List<LineBreakdown> lines,
        List<AppliedPromotion> appliedPromotions,
        BigDecimal subtotal,
        BigDecimal totalDiscount,
        BigDecimal total) {
}
