package com.promoquoter.pricing;

public interface PricingRule {

    PromotionDefinition definition();

    int stage();

    void apply(PricingContext context);
}
