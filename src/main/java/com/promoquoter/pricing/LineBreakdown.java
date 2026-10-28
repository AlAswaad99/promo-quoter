package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.util.UUID;

public record LineBreakdown(
        UUID productId,
        String name,
        String category,
        BigDecimal unitPrice,
        int qty,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total) {

    static LineBreakdown from(PricedLine line) {
        return new LineBreakdown(line.productId(), line.name(), line.category(), line.unitPrice(), line.qty(),
                line.subtotal(), line.discount(), line.total());
    }
}
