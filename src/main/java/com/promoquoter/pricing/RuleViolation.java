package com.promoquoter.pricing;

import java.util.UUID;

public record RuleViolation(UUID promotionId, String name, String message) {
}
