package com.promoquoter.pricing.rules;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.ActionKind;
import com.promoquoter.pricing.ActionRuleProvider;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.RuleDefinition;

@Component
public class AmountOffCartRuleProvider implements ActionRuleProvider {

    @Override
    public ActionKind kind() {
        return ActionKind.AMOUNT_OFF_CART;
    }

    @Override
    public PricingRule create(RuleDefinition definition) {
        if (!(definition.action() instanceof ActionSpec.AmountOffCart)) {
            throw new IllegalArgumentException("Expected a AMOUNT_OFF_CART action but got " + definition.action().kind());
        }
        return new AmountOffCartRule(definition);
    }
}
