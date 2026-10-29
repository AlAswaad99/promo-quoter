package com.promoquoter.pricing.rules;

import java.math.BigDecimal;

import com.promoquoter.common.money.Money;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.PricedLine;
import com.promoquoter.pricing.RuleDefinition;

public final class PercentOffRule extends LineActionRule {

    private final BigDecimal percent;

    public PercentOffRule(RuleDefinition definition) {
        super(definition);
        this.percent = ((ActionSpec.PercentOff) definition.action()).percent();
    }

    @Override
    protected void applyToLine(PricedLine line) {
        line.applyDiscount(Money.percentOf(line.total(), percent));
    }
}
