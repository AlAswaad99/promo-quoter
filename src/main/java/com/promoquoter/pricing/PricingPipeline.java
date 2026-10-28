package com.promoquoter.pricing;

import java.util.Comparator;
import java.util.List;

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
        rules.forEach(rule -> rule.apply(context));
    }
}
