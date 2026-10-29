package com.promoquoter.cart.dto;

import java.util.UUID;

public record RuleViolationEntry(UUID promotionId, String name, String message) {
}
