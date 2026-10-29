package com.promoquoter.pricing.model;

import java.util.UUID;

public record RuleViolation(UUID promotionId, String name, String message) {
}
