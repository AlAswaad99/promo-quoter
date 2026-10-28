package com.promoquoter.order;

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
        @MoneyField BigDecimal total) {

    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getCustomerSegment(),
                order.getLines().stream()
                        .map(line -> new QuoteResponse.Line(line.productId(), line.name(), line.category(),
                                line.unitPrice(), line.qty(), line.subtotal(), line.discount(), line.total()))
                        .toList(),
                order.getAppliedPromotions().stream()
                        .map(applied -> new QuoteResponse.AppliedPromotionEntry(applied.sequence(),
                                applied.promotionId(), applied.name(), applied.type(), applied.productId(),
                                applied.amount()))
                        .toList(),
                order.getSubtotal(),
                order.getTotalDiscount(),
                order.getTotal());
    }
}
