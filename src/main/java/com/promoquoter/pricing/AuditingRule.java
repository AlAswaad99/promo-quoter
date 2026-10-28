package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.util.List;

public final class AuditingRule implements PricingRule {

    private final PricingRule delegate;

    public AuditingRule(PricingRule delegate) {
        this.delegate = delegate;
    }

    @Override
    public PromotionDefinition definition() {
        return delegate.definition();
    }

    @Override
    public int stage() {
        return delegate.stage();
    }

    @Override
    public void apply(PricingContext context) {
        List<PricedLine> lines = context.lines();
        List<BigDecimal> totalsBefore = lines.stream().map(PricedLine::total).toList();
        delegate.apply(context);
        for (int index = 0; index < lines.size(); index++) {
            BigDecimal discounted = totalsBefore.get(index).subtract(lines.get(index).total());
            if (discounted.signum() > 0) {
                context.recordApplied(delegate.definition(), lines.get(index), discounted);
            }
        }
    }
}
