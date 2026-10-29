package com.promoquoter.pricing;

public interface ActionRuleProvider {

    ActionKind kind();

    PricingRule create(RuleDefinition definition);
}
