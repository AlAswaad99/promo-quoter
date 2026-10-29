package com.promoquoter.pricing.rules;

import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.RuleDefinition;

public abstract class CartActionRule extends GenericRule {

    protected CartActionRule(RuleDefinition definition) {
        super(definition);
    }

    @Override
    protected final void execute(PricingContext context) {
        if (ruleDefinition().target() != null && targetLines(context).isEmpty()) {
            return;
        }
        applyToCart(context);
    }

    protected abstract void applyToCart(PricingContext context);
}
