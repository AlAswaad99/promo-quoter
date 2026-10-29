package com.promoquoter.pricing.rules;

import java.util.UUID;

import com.promoquoter.pricing.engine.PricingContext;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionSpec;

public final class FreeGiftRule extends CartActionRule {

    private final UUID productId;
    private final int qty;

    public FreeGiftRule(RuleDefinition definition) {
        super(definition);
        ActionSpec.FreeGift spec = (ActionSpec.FreeGift) definition.action();
        this.productId = spec.productId();
        this.qty = spec.qty();
    }

    @Override
    protected void applyToCart(PricingContext context) {
        int granted = context.requestGift(ruleDefinition(), productId, qty);
        if (granted < qty) {
            context.skip(ruleDefinition(), "gift limit of " + PricingContext.MAX_GIFT_UNITS + " units per order reached");
        }
    }
}
