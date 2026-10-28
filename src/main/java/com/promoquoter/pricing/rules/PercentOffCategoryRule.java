package com.promoquoter.pricing.rules;

import com.promoquoter.common.money.Money;
import com.promoquoter.pricing.PricedLine;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.PromotionDefinition;

public final class PercentOffCategoryRule implements PricingRule {

    public static final int STAGE = 100;

    private final PromotionDefinition definition;

    public PercentOffCategoryRule(PromotionDefinition definition) {
        this.definition = definition;
    }

    @Override
    public PromotionDefinition definition() {
        return definition;
    }

    @Override
    public int stage() {
        return STAGE;
    }

    @Override
    public void apply(PricingContext context) {
        for (PricedLine line : context.lines()) {
            if (definition.category().equals(line.category())) {
                line.applyDiscount(Money.percentOf(line.total(), definition.percent()));
            }
        }
    }
}
