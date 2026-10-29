package com.promoquoter.pricing.engine;

import java.util.Comparator;
import java.util.List;

import com.promoquoter.pricing.model.RuleKind;

public final class PricingPipeline {

    private static final Comparator<PricingRule> EXECUTION_ORDER = Comparator
            .comparingInt(PricingRule::stage)
            .thenComparingInt(rule -> rule.definition().priority())
            .thenComparing(rule -> rule.definition().createdAt())
            .thenComparing(rule -> rule.definition().id());

    private final List<PricingRule> rules;

    public PricingPipeline(List<PricingRule> rules) {
        this.rules = rules.stream().sorted(EXECUTION_ORDER).toList();
    }

    public void run(PricingContext context) {
        for (PricingRule rule : rules) {
            if (context.isStopped() && rule.kind() == RuleKind.PRICING) {
                context.skip(rule.definition(), "stopped by '" + context.stoppedBy() + "'");
            } else {
                rule.apply(context);
            }
        }
    }
}
