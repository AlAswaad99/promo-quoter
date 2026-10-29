package com.promoquoter.cart.domain;

import com.promoquoter.cart.dto.QuoteResponse;
import com.promoquoter.cart.dto.RuleViolationEntry;
import com.promoquoter.common.money.Money;
import com.promoquoter.pricing.AppliedPromotion;
import com.promoquoter.pricing.CartAdjustment;
import com.promoquoter.pricing.LineBreakdown;
import com.promoquoter.pricing.PriceBreakdown;
import com.promoquoter.pricing.RuleViolation;
import com.promoquoter.pricing.SkippedRule;

final class QuoteMapper {

    private QuoteMapper() {
    }

    static QuoteResponse toResponse(QuotedCart quoted) {
        PriceBreakdown breakdown = quoted.breakdown();
        return new QuoteResponse(
                breakdown.customerSegment(),
                breakdown.lines().stream().map(QuoteMapper::line).toList(),
                breakdown.appliedPromotions().stream().map(QuoteMapper::applied).toList(),
                breakdown.subtotal(),
                breakdown.totalDiscount(),
                breakdown.total(),
                breakdown.shipping(),
                breakdown.adjustments().stream().map(QuoteMapper::adjustment).toList(),
                quoted.gifts().stream().map(QuoteMapper::gift).toList(),
                quoted.skipped().stream().map(QuoteMapper::skipped).toList());
    }

    static RuleViolationEntry toEntry(RuleViolation violation) {
        return new RuleViolationEntry(violation.promotionId(), violation.name(), violation.message());
    }

    private static QuoteResponse.Line line(LineBreakdown line) {
        return new QuoteResponse.Line(line.productId(), line.name(), line.category(), line.unitPrice(), line.qty(),
                line.subtotal(), line.discount(), line.total());
    }

    private static QuoteResponse.AppliedPromotionEntry applied(AppliedPromotion applied) {
        return new QuoteResponse.AppliedPromotionEntry(applied.sequence(), applied.promotionId(), applied.name(),
                applied.type(), applied.productId(), applied.amount());
    }

    private static QuoteResponse.AdjustmentEntry adjustment(CartAdjustment adjustment) {
        return new QuoteResponse.AdjustmentEntry(adjustment.sequence(), adjustment.promotionId(), adjustment.name(),
                adjustment.action(), adjustment.direction(), adjustment.amount());
    }

    private static QuoteResponse.GiftEntry gift(GiftLine gift) {
        return new QuoteResponse.GiftEntry(gift.productId(), gift.name(), gift.category(), gift.qty(), Money.ZERO,
                Money.ZERO, gift.promotionId(), gift.promotionName());
    }

    private static QuoteResponse.SkippedEntry skipped(SkippedRule skipped) {
        return new QuoteResponse.SkippedEntry(skipped.promotionId(), skipped.name(), skipped.reason());
    }
}
