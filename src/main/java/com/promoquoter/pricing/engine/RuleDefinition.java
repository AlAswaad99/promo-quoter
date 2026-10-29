package com.promoquoter.pricing.engine;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.promoquoter.pricing.condition.Condition;
import com.promoquoter.pricing.model.ActionSpec;
import com.promoquoter.pricing.model.CustomerSegment;
import com.promoquoter.pricing.model.PromotionType;
import com.promoquoter.pricing.model.RuleInfo;
import com.promoquoter.pricing.model.RuleKind;

public record RuleDefinition(
        UUID id,
        PromotionType type,
        String name,
        RuleKind kind,
        boolean active,
        int priority,
        int stage,
        boolean stopAfter,
        Instant createdAt,
        Set<CustomerSegment> eligibleSegments,
        Condition when,
        Condition target,
        ActionSpec action) implements RuleInfo {

    public RuleDefinition {
        if (action == null) {
            throw new IllegalArgumentException("action is required");
        }
        if (action.kind().ruleKind() != kind) {
            throw new IllegalArgumentException("action " + action.kind() + " cannot be used in a " + kind + " rule");
        }
        eligibleSegments = eligibleSegments == null || eligibleSegments.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(eligibleSegments));
    }

    public boolean isEligibleFor(CustomerSegment segment) {
        return active && (eligibleSegments.isEmpty() || eligibleSegments.contains(segment));
    }
}
