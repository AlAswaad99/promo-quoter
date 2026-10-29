package com.promoquoter.pricing.rules;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.engine.ActionRuleProvider;
import com.promoquoter.pricing.engine.PricingRule;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.ActionSpec;

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
