package com.promoquoter.pricing.rules;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.engine.ActionRuleProvider;
import com.promoquoter.pricing.engine.PricingRule;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.ActionSpec;

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
