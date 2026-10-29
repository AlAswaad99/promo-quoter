package com.promoquoter.pricing.model;

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
}
