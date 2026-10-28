package com.promoquoter.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.promoquoter.common.money.MoneyField;
import com.promoquoter.pricing.AppliedPromotion;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.LineBreakdown;
import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.pricing.PromotionType;

public record QuoteResponse(
        CustomerSegment customerSegment,
        List<Line> lines,
        List<AppliedPromotionEntry> appliedPromotions,
        @MoneyField BigDecimal subtotal,
        @MoneyField BigDecimal totalDiscount,
        @MoneyField BigDecimal total) {

    public record Line(
            UUID productId,
            String name,
            String category,
            @MoneyField BigDecimal unitPrice,
            int qty,
            @MoneyField BigDecimal subtotal,
            @MoneyField BigDecimal discount,
            @MoneyField BigDecimal total) {

        static Line from(LineBreakdown line) {
            return new Line(line.productId(), line.name(), line.category(), line.unitPrice(), line.qty(),
                    line.subtotal(), line.discount(), line.total());
        }
    }

    public record AppliedPromotionEntry(
            int sequence,
            UUID promotionId,
            String name,
            PromotionType type,
            UUID productId,
            @MoneyField BigDecimal amount) {

        static AppliedPromotionEntry from(AppliedPromotion applied) {
            return new AppliedPromotionEntry(applied.sequence(), applied.promotionId(), applied.name(),
                    applied.type(), applied.productId(), applied.amount());
        }
    }

    public static QuoteResponse from(PriceBreakdown breakdown) {
        return new QuoteResponse(
                breakdown.customerSegment(),
                breakdown.lines().stream().map(Line::from).toList(),
                breakdown.appliedPromotions().stream().map(AppliedPromotionEntry::from).toList(),
                breakdown.subtotal(),
                breakdown.totalDiscount(),
                breakdown.total());
    }
}
