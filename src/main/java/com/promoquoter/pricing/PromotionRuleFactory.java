package com.promoquoter.pricing;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PromotionRuleFactory {

    private final Map<PromotionType, RuleProvider> providers = new EnumMap<>(PromotionType.class);

    public PromotionRuleFactory(List<RuleProvider> ruleProviders) {
        for (RuleProvider provider : ruleProviders) {
            if (providers.put(provider.type(), provider) != null) {
                throw new IllegalStateException("Multiple rule providers registered for " + provider.type());
            }
        }
        List<PromotionType> missing = Arrays.stream(PromotionType.values())
                .filter(type -> !providers.containsKey(type))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No rule provider registered for " + missing);
        }
    }

    public List<PricingRule> create(List<PromotionDefinition> definitions, CustomerSegment customerSegment) {
        return definitions.stream()
                .filter(definition -> definition.isEligibleFor(customerSegment))
                .<PricingRule>map(definition -> new AuditingRule(providers.get(definition.type()).create(definition)))
                .toList();
    }
}
