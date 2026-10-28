package com.promoquoter.pricing.rules;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.PromotionDefinition;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.RuleProvider;

@Component
public class BuyXGetYRuleProvider implements RuleProvider {

    @Override
    public PromotionType type() {
        return PromotionType.BUY_X_GET_Y;
    }

    @Override
    public PricingRule create(PromotionDefinition definition) {
        Objects.requireNonNull(definition.productId(), "productId");
        Objects.requireNonNull(definition.buyQty(), "buyQty");
        Objects.requireNonNull(definition.freeQty(), "freeQty");
        return new BuyXGetYRule(definition);
    }
}
