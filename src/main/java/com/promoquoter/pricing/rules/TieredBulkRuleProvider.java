package com.promoquoter.pricing.rules;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.PromotionDefinition;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.RuleProvider;

@Component
public class TieredBulkRuleProvider implements RuleProvider {

    @Override
    public PromotionType type() {
        return PromotionType.TIERED_BULK;
    }

    @Override
    public PricingRule create(PromotionDefinition definition) {
        Objects.requireNonNull(definition.productId(), "productId");
        Objects.requireNonNull(definition.minQty(), "minQty");
        Objects.requireNonNull(definition.percent(), "percent");
        return new TieredBulkRule(definition);
    }
}
