package com.promoquoter.pricing.rules;

import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.RuleDefinition;

public final class RejectRule extends CartActionRule {

    private final String message;

    public RejectRule(RuleDefinition definition) {
        super(definition);
        this.message = ((ActionSpec.Reject) definition.action()).message();
    }

    @Override
    protected void applyToCart(PricingContext context) {
        context.reject(ruleDefinition(), message);
    }
}
