package com.promoquoter.pricing.rules;

import com.promoquoter.common.money.Money;
import com.promoquoter.pricing.PricedLine;
import com.promoquoter.pricing.PricingContext;
import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.PromotionDefinition;

public final class BuyXGetYRule implements PricingRule {

    public static final int STAGE = 200;

    private final PromotionDefinition definition;

    public BuyXGetYRule(PromotionDefinition definition) {
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
        int groupSize = definition.buyQty() + definition.freeQty();
        for (PricedLine line : context.lines()) {
            if (definition.productId().equals(line.productId())) {
                long freeUnits = (long) (line.qty() / groupSize) * definition.freeQty();
                if (freeUnits > 0) {
                    line.applyDiscount(Money.proportion(line.total(), freeUnits, line.qty()));
                }
            }
        }
    }
}
