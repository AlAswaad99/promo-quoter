package com.promoquoter.pricing.rules;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.engine.ActionRuleProvider;
import com.promoquoter.pricing.engine.PricingRule;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.ActionSpec;

@Component
public class FreeUnitsRuleProvider implements ActionRuleProvider {

    @Override
    public ActionKind kind() {
        return ActionKind.FREE_UNITS;
    }

    @Override
    public PricingRule create(RuleDefinition definition) {
        if (!(definition.action() instanceof ActionSpec.FreeUnits)) {
            throw new IllegalArgumentException("Expected a FREE_UNITS action but got " + definition.action().kind());
        }
        return new FreeUnitsRule(definition);
    }
}
