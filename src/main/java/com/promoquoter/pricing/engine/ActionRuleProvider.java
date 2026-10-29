package com.promoquoter.pricing.engine;

import com.promoquoter.pricing.model.ActionKind;

public interface ActionRuleProvider {

    ActionKind kind();

    PricingRule create(RuleDefinition definition);
}
