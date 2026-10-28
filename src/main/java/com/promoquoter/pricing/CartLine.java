package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.util.UUID;

public record CartLine(UUID productId, String name, String category, BigDecimal unitPrice, int qty) {
}
