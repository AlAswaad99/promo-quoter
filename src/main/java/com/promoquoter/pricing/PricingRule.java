package com.promoquoter.pricing;

public interface PricingRule {

    RuleInfo definition();

    int stage();

    void apply(PricingContext context);
}
