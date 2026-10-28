package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.promoquoter.common.money.Money;

public final class PricingContext {

    private final CustomerSegment customerSegment;
    private final List<PricedLine> lines;
    private final List<AppliedPromotion> appliedPromotions = new ArrayList<>();

    private PricingContext(CustomerSegment customerSegment, List<PricedLine> lines) {
        this.customerSegment = customerSegment;
        this.lines = lines;
    }

    public static PricingContext start(List<CartLine> cartLines, CustomerSegment customerSegment) {
        return new PricingContext(customerSegment, cartLines.stream().map(PricedLine::new).toList());
    }

    public CustomerSegment customerSegment() {
        return customerSegment;
    }

    public List<PricedLine> lines() {
        return lines;
    }

    public List<AppliedPromotion> appliedPromotions() {
        return Collections.unmodifiableList(appliedPromotions);
    }

    public void recordApplied(PromotionDefinition promotion, PricedLine line, BigDecimal amount) {
        appliedPromotions.add(new AppliedPromotion(appliedPromotions.size() + 1, promotion.id(), promotion.name(),
                promotion.type(), line.productId(), amount));
    }

    public PriceBreakdown toBreakdown() {
        BigDecimal subtotal = sum(PricedLine::subtotal);
        BigDecimal total = sum(PricedLine::total);
        return new PriceBreakdown(customerSegment, lines.stream().map(LineBreakdown::from).toList(),
                List.copyOf(appliedPromotions), subtotal, subtotal.subtract(total), total);
    }

    private BigDecimal sum(Function<PricedLine, BigDecimal> selector) {
        return lines.stream().map(selector).reduce(Money.ZERO, BigDecimal::add);
    }
}
