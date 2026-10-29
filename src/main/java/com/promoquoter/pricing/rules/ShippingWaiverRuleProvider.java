package com.promoquoter.pricing.rules;

import org.springframework.stereotype.Component;

import com.promoquoter.pricing.engine.ActionRuleProvider;
import com.promoquoter.pricing.engine.PricingRule;
import com.promoquoter.pricing.engine.RuleDefinition;
import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.ActionSpec;

@Component
public class ShippingWaiverRuleProvider implements ActionRuleProvider {

    @Override
    public ActionKind kind() {
        return ActionKind.SHIPPING_WAIVER;
    }

    @Override
    public PricingRule create(RuleDefinition definition) {
        if (!(definition.action() instanceof ActionSpec.ShippingWaiver)) {
            throw new IllegalArgumentException("Expected a SHIPPING_WAIVER action but got " + definition.action().kind());
        }
        return new ShippingWaiverRule(definition);
    }
}
