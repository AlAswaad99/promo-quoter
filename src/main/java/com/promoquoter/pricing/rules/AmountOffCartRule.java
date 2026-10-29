package com.promoquoter.pricing.rules;

import java.math.BigDecimal;

import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.RuleDefinition;

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
