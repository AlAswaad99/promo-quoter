package com.promoquoter.pricing;

public interface PricingRule {

    RuleInfo definition();

    int stage();

    void apply(PricingContext context);

    default RuleKind kind() {
        return RuleKind.PRICING;
    }

    default boolean stopAfter() {
        return false;
    }

    default boolean targetsLines() {
        return true;
    }
}
