package com.promoquoter.pricing.rules;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.engine.ActionRuleProvider;
import com.promoquoter.pricing.engine.PricingRule;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.ActionSpec;

@Component
public class AmountOffRuleProvider implements ActionRuleProvider {

    @Override
    public ActionKind kind() {
        return ActionKind.AMOUNT_OFF;
    }

    @Override
    public PricingRule create(RuleDefinition definition) {
        if (!(definition.action() instanceof ActionSpec.AmountOff)) {
            throw new IllegalArgumentException("Expected a AMOUNT_OFF action but got " + definition.action().kind());
        }
        return new AmountOffRule(definition);
    }
}
