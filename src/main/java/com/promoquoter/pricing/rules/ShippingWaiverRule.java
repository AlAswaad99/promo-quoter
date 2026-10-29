package com.promoquoter.pricing.rules;

import com.promoquoter.pricing.engine.PricingContext;
import com.promoquoter.pricing.engine.RuleDefinition;

public final class ShippingWaiverRule extends CartActionRule {

    public ShippingWaiverRule(RuleDefinition definition) {
        super(definition);
    }

    @Override
    protected void applyToCart(PricingContext context) {
        context.waiveShipping(ruleDefinition());
    }
}
