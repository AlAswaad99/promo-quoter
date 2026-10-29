package com.promoquoter.pricing.model;

import java.util.UUID;

public record SkippedRule(UUID promotionId, String name, String reason) {
}
