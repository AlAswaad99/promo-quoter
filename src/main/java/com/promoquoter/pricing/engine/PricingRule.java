package com.promoquoter.pricing.engine;

import com.promoquoter.pricing.model.RuleInfo;
import com.promoquoter.pricing.model.RuleKind;

public interface PricingRule {

    RuleInfo definition();

    int stage();

    void apply(PricingContext context);

    default RuleKind kind() {
        return RuleKind.PRICING;
    }

    default boolean stopAfter() {
        return false;
    }

    default boolean targetsLines() {
        return true;
    }
}
