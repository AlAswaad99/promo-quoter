package com.promoquoter.pricing;

import java.util.UUID;

public record SkippedRule(UUID promotionId, String name, String reason) {
}
