package com.promoquoter.pricing;

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
