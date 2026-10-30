package com.promoquoter.pricing.engine;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.promoquoter.common.observability.LogEvent;
import com.promoquoter.pricing.model.RuleInfo;
import com.promoquoter.pricing.model.RuleKind;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
        BigDecimal lineAmount = BigDecimal.ZERO;
        for (int index = 0; index < lines.size(); index++) {
            BigDecimal amount = totalsBefore.get(index).subtract(lines.get(index).total());
            if (amount.signum() > 0) {
                context.recordApplied(delegate.definition(), lines.get(index), amount);
                discounted.add(lines.get(index));
                lineAmount = lineAmount.add(amount);
            }
        }
        if (log.isDebugEnabled()) {
            LogEvent.PRICING_RULE_EVALUATED.at(log)
                    .with("ruleId", delegate.definition().id())
                    .with("ruleName", delegate.definition().name())
                    .with("stage", delegate.stage())
                    .with("fired", !discounted.isEmpty() || cartEffects(context) > cartEffectsBefore)
                    .with("lineAmount", lineAmount)
                    .message("Rule evaluated");
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
