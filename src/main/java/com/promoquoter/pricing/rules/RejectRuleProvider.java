package com.promoquoter.pricing.rules;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.ActionKind;
import com.promoquoter.pricing.ActionRuleProvider;
import com.promoquoter.pricing.ActionSpec;
import com.promoquoter.pricing.PricingRule;
import com.promoquoter.pricing.RuleDefinition;

@Component
public class RejectRuleProvider implements ActionRuleProvider {

    @Override
    public ActionKind kind() {
        return ActionKind.REJECT;
    }

    @Override
    public PricingRule create(RuleDefinition definition) {
        if (!(definition.action() instanceof ActionSpec.Reject)) {
            throw new IllegalArgumentException("Expected a REJECT action but got " + definition.action().kind());
        }
        return new RejectRule(definition);
    }
}
