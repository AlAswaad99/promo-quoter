package com.promoquoter.pricing.rules;

import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.RuleDefinition;

public final class ShippingWaiverRule extends CartActionRule {

    public ShippingWaiverRule(RuleDefinition definition) {
        super(definition);
    }

    @Override
    protected void applyToCart(PricingContext context) {
        context.waiveShipping(ruleDefinition());
    }
}
