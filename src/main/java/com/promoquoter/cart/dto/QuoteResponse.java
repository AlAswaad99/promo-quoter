package com.promoquoter.cart.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.promoquoter.common.money.MoneyField;
import com.promoquoter.pricing.ActionKind;
import com.promoquoter.pricing.AdjustmentDirection;
import com.promoquoter.pricing.CustomerSegment;
import com.promoquoter.pricing.PromotionType;

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
    }

    public record SkippedEntry(UUID promotionId, String name, String reason) {
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
    }

    public record AppliedPromotionEntry(
            int sequence,
            UUID promotionId,
            String name,
            PromotionType type,
            UUID productId,
            @MoneyField BigDecimal amount) {
    }
}
