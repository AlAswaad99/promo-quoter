package com.promoquoter.cart;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.promoquoter.common.money.Money;
import com.promoquoter.common.money.MoneyField;
import com.promoquoter.pricing.ActionKind;
import com.promoquoter.pricing.AdjustmentDirection;
import com.promoquoter.pricing.AppliedPromotion;
import com.promoquoter.pricing.CartAdjustment;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.LineBreakdown;
import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.SkippedRule;

public record QuoteResponse(
        CustomerSegment customerSegment,
        List<Line> lines,
        List<AppliedPromotionEntry> appliedPromotions,
        @MoneyField BigDecimal subtotal,
        @MoneyField BigDecimal totalDiscount,
        @MoneyField BigDecimal total,
        @MoneyField BigDecimal shipping,
        List<AdjustmentEntry> adjustments,
        List<GiftEntry> gifts,
        List<SkippedEntry> skipped) {

    public record AdjustmentEntry(
            int sequence,
            UUID promotionId,
            String name,
            ActionKind action,
            AdjustmentDirection direction,
            @MoneyField BigDecimal amount) {

        static AdjustmentEntry from(CartAdjustment adjustment) {
            return new AdjustmentEntry(adjustment.sequence(), adjustment.promotionId(), adjustment.name(),
                    adjustment.action(), adjustment.direction(), adjustment.amount());
        }
    }

    public record GiftEntry(
            UUID productId,
            String name,
            String category,
            int qty,
            @MoneyField BigDecimal unitPrice,
            @MoneyField BigDecimal total,
            UUID promotionId,
            String promotionName) {

        static GiftEntry from(GiftLine gift) {
            return new GiftEntry(gift.productId(), gift.name(), gift.category(), gift.qty(), Money.ZERO, Money.ZERO,
                    gift.promotionId(), gift.promotionName());
        }
    }

    public record SkippedEntry(UUID promotionId, String name, String reason) {

        static SkippedEntry from(SkippedRule skipped) {
            return new SkippedEntry(skipped.promotionId(), skipped.name(), skipped.reason());
        }
    }

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

    public static QuoteResponse from(QuotedCart quoted) {
        PriceBreakdown breakdown = quoted.breakdown();
        return new QuoteResponse(
                breakdown.customerSegment(),
                breakdown.lines().stream().map(Line::from).toList(),
                breakdown.appliedPromotions().stream().map(AppliedPromotionEntry::from).toList(),
                breakdown.subtotal(),
                breakdown.totalDiscount(),
                breakdown.total(),
                breakdown.shipping(),
                breakdown.adjustments().stream().map(AdjustmentEntry::from).toList(),
                quoted.gifts().stream().map(GiftEntry::from).toList(),
                quoted.skipped().stream().map(SkippedEntry::from).toList());
    }
}
