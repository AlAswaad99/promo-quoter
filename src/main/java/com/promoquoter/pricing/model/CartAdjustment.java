package com.promoquoter.pricing.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CartAdjustment(
        int sequence,
        UUID promotionId,
        String name,
        ActionKind action,
        AdjustmentDirection direction,
        BigDecimal amount) {
}
