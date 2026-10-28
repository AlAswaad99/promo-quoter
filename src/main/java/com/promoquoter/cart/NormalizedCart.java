package com.promoquoter.cart;

import java.util.List;
import java.util.UUID;

import com.promoquoter.pricing.CustomerSegment;

public record NormalizedCart(List<Item> items, CustomerSegment customerSegment) {

    public record Item(UUID productId, int qty) {
    }
}
