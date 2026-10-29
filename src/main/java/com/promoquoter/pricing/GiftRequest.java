package com.promoquoter.pricing;

import java.util.UUID;

public record GiftRequest(UUID promotionId, String name, UUID productId, int qty) {
}
