package com.promoquoter.pricing;

public interface RuleProvider {

    PromotionType type();

    PricingRule create(PromotionDefinition definition);
}
