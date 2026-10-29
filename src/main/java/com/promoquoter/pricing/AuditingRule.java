package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class AuditingRule implements PricingRule {

    private final PricingRule delegate;

    public AuditingRule(PricingRule delegate) {
        this.delegate = delegate;
    }

    @Override
    public RuleInfo definition() {
        return delegate.definition();
    }

    @Override
    public int stage() {
        return delegate.stage();
    }

    @Override
    public RuleKind kind() {
        return delegate.kind();
    }

    @Override
    public boolean stopAfter() {
        return delegate.stopAfter();
    }

    @Override
    public boolean targetsLines() {
        return delegate.targetsLines();
    }

    @Override
    public void apply(PricingContext context) {
        List<PricedLine> lines = context.lines();
        List<BigDecimal> totalsBefore = lines.stream().map(PricedLine::total).toList();
        int cartEffectsBefore = cartEffects(context);
        delegate.apply(context);
        List<PricedLine> discounted = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            BigDecimal amount = totalsBefore.get(index).subtract(lines.get(index).total());
            if (amount.signum() > 0) {
                context.recordApplied(delegate.definition(), lines.get(index), amount);
                discounted.add(lines.get(index));
            }
        }
        if (delegate.stopAfter()) {
            stopFurtherRules(context, discounted, cartEffects(context) > cartEffectsBefore);
        }
    }

    private void stopFurtherRules(PricingContext context, List<PricedLine> discounted, boolean cartEffect) {
        if (delegate.targetsLines()) {
            discounted.forEach(context::freeze);
        } else if (cartEffect) {
            context.stop(delegate.definition());
        }
    }

    private static int cartEffects(PricingContext context) {
        return context.adjustments().size() + context.gifts().size() + context.violations().size();
    }
}
