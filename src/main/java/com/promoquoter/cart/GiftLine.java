package com.promoquoter.cart;

import java.util.UUID;

public record GiftLine(UUID productId, String name, String category, int qty, UUID promotionId,
        String promotionName) {
}
