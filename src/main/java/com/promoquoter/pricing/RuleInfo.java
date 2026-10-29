package com.promoquoter.pricing;

import java.time.Instant;
import java.util.UUID;

public interface RuleInfo {

    UUID id();

    String name();

    PromotionType type();

    int priority();

    Instant createdAt();
}
