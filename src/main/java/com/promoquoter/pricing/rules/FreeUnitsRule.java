package com.promoquoter.pricing.rules;

import com.promoquoter.common.money.Money;
import com.promoquoter.pricing.engine.PricedLine;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionSpec;

public final class FreeUnitsRule extends LineActionRule {

    private final int buyQty;
    private final int freeQty;

    public FreeUnitsRule(RuleDefinition definition) {
        super(definition);
        ActionSpec.FreeUnits spec = (ActionSpec.FreeUnits) definition.action();
        this.buyQty = spec.buyQty();
        this.freeQty = spec.freeQty();
    }

    @Override
    protected void applyToLine(PricedLine line) {
        int groupSize = buyQty + freeQty;
        long freeUnits = (long) (line.qty() / groupSize) * freeQty;
        if (freeUnits > 0) {
            line.applyDiscount(Money.proportion(line.total(), freeUnits, line.qty()));
        }
    }
}
