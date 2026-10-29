package com.promoquoter.pricing.rules;

import java.math.BigDecimal;

import com.promoquoter.pricing.engine.PricingContext;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionSpec;

public final class AmountOffCartRule extends CartActionRule {

    private final BigDecimal amount;

    public AmountOffCartRule(RuleDefinition definition) {
        super(definition);
        this.amount = ((ActionSpec.AmountOffCart) definition.action()).amount();
    }

    @Override
    protected void applyToCart(PricingContext context) {
        context.applyCartDiscount(ruleDefinition(), amount);
    }
}
