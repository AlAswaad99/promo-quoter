package com.promoquoter.pricing.rules;

import com.promoquoter.pricing.engine.PricingContext;
import com.promoquoter.pricing.engine.RuleDefinition;

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
