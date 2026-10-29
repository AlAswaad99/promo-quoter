package com.promoquoter.pricing.engine;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.promoquoter.pricing.model.ActionKind;
import com.promoquoter.pricing.model.CustomerSegment;

public final class PromotionRuleFactory {

    private final Map<ActionKind, ActionRuleProvider> providers = new EnumMap<>(ActionKind.class);

    public PromotionRuleFactory(List<ActionRuleProvider> ruleProviders) {
        for (ActionRuleProvider provider : ruleProviders) {
            if (providers.put(provider.kind(), provider) != null) {
                throw new IllegalStateException("Multiple rule providers registered for " + provider.kind());
            }
        }
        List<ActionKind> missing = Arrays.stream(ActionKind.values())
                .filter(kind -> !providers.containsKey(kind))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No rule provider registered for " + missing);
        }
    }

    public List<PricingRule> create(List<RuleDefinition> definitions, CustomerSegment customerSegment) {
        return definitions.stream()
                .filter(definition -> definition.isEligibleFor(customerSegment))
                .<PricingRule>map(definition -> new AuditingRule(providers.get(definition.action().kind()).create(definition)))
                .toList();
    }
}
