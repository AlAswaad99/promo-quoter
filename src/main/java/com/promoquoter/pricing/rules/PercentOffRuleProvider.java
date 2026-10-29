package com.promoquoter.pricing.rules;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.ActionKind;
import com.promoquoter.pricing.ActionRuleProvider;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.RuleDefinition;

@Component
public class PercentOffRuleProvider implements ActionRuleProvider {

    @Override
    public ActionKind kind() {
        return ActionKind.PERCENT_OFF;
    }

    @Override
    public PricingRule create(RuleDefinition definition) {
        if (!(definition.action() instanceof ActionSpec.PercentOff)) {
            throw new IllegalArgumentException("Expected a PERCENT_OFF action but got " + definition.action().kind());
        }
        return new PercentOffRule(definition);
    }
}
