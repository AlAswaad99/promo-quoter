package com.promoquoter.pricing.rules;

import java.math.BigDecimal;

import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.RuleDefinition;

public final class SurchargeRule extends CartActionRule {

    private final BigDecimal amount;

    public SurchargeRule(RuleDefinition definition) {
        super(definition);
        this.amount = ((ActionSpec.Surcharge) definition.action()).amount();
    }

    @Override
    protected void applyToCart(PricingContext context) {
        context.addSurcharge(ruleDefinition(), amount);
    }
}
