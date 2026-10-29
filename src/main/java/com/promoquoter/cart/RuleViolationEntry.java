package com.promoquoter.cart;

import java.util.UUID;

import com.promoquoter.pricing.RuleViolation;

public record RuleViolationEntry(UUID promotionId, String name, String message) {

    static RuleViolationEntry from(RuleViolation violation) {
        return new RuleViolationEntry(violation.promotionId(), violation.name(), violation.message());
    }
}
