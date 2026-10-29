package com.promoquoter.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.promoquoter.cart.QuoteResponse;
import com.promoquoter.common.money.MoneyField;
import com.promoquoter.pricing.CustomerSegment;

public record OrderResponse(
        UUID orderId,
        OrderStatus status,
        Instant createdAt,
        CustomerSegment customerSegment,
        List<QuoteResponse.Line> lines,
        List<QuoteResponse.AppliedPromotionEntry> appliedPromotions,
        @MoneyField BigDecimal subtotal,
        @MoneyField BigDecimal totalDiscount,
        @MoneyField BigDecimal total,
        @MoneyField BigDecimal shipping,
        List<QuoteResponse.AdjustmentEntry> adjustments,
        List<QuoteResponse.GiftEntry> gifts,
        List<String> notes) {
}
