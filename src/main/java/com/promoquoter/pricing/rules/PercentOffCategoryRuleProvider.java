package com.promoquoter.pricing.rules;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.PromotionDefinition;
import com.promoquoter.pricing.PromotionType;
import com.promoquoter.pricing.RuleProvider;

@Component
public class PercentOffCategoryRuleProvider implements RuleProvider {

    @Override
    public PromotionType type() {
        return PromotionType.PERCENT_OFF_CATEGORY;
    }

    @Override
    public PricingRule create(PromotionDefinition definition) {
        Objects.requireNonNull(definition.category(), "category");
        Objects.requireNonNull(definition.percent(), "percent");
        return new PercentOffCategoryRule(definition);
    }
}
