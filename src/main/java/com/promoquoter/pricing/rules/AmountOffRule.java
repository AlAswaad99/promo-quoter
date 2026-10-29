package com.promoquoter.pricing.rules;

import java.math.BigDecimal;

import com.promoquoter.pricing.engine.PricedLine;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionSpec;

public final class AmountOffRule extends LineActionRule {

    private final BigDecimal amount;

    public AmountOffRule(RuleDefinition definition) {
        super(definition);
        this.amount = ((ActionSpec.AmountOff) definition.action()).amount();
    }

    @Override
    protected void applyToLine(PricedLine line) {
        line.applyDiscount(amount);
    }
}
